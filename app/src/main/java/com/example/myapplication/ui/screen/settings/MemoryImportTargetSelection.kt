package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.myapplication.model.MemoryImportTarget
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.ContextImportPreview

@Composable
internal fun MemoryImportTargetSelection(
    preview: ContextImportPreview,
    isBusy: Boolean,
    onTargetSelected: (String, MemoryImportTarget?) -> Unit,
    onReplaceStates: (Boolean) -> Unit,
) {
    val palette = rememberSettingsPalette()
    var selectingGroup by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(NarraUi.space12)) {
        Text(
            "记忆恢复目标", modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium, color = palette.title
        )
        Text(
            "每个原空间和扮演身份分别选择目标。已有状态不会自动覆盖。",
            style = MaterialTheme.typography.bodyMedium, color = palette.body
        )
        if (preview.memorySourceCount > 0) Text(
            "保留 ${preview.memorySourceCount} 份来源材料",
            color = palette.body
        )
        for (group in preview.memoryGroups) {
            Column(verticalArrangement = Arrangement.spacedBy(NarraUi.space4)) {
                Text("${group.purpose.label} · ${group.spaceId.take(16)}", color = palette.title)
                Text(
                    "原角色 ${group.characterId} · 原身份 ${group.personaKey} · ${group.itemCount} 条档案",
                    style = MaterialTheme.typography.bodySmall, color = palette.body
                )
                val target = preview.memoryTargets[group.key]
                val label = preview.memoryTargetOptions.firstOrNull { it.target == target }?.label
                    ?: "选择本机角色、空间与身份"
                TextButton(
                    onClick = { selectingGroup = group.key }, enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = NarraMinimumTouchTarget)
                        .testTag("memory_import_target_${group.key}"),
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.title),
                    contentPadding = PaddingValues(
                        horizontal = NarraUi.space12,
                        vertical = NarraUi.space8
                    ),
                ) { Text(label) }
            }
        }
        if (preview.memoryStateConflicts.isNotEmpty()) {
            val multiple = preview.memoryStateConflicts.any { it.multipleImportedHeads }
            Text(
                if (multiple) "多个导入状态落入同一位置，请更换目标空间或身份。" else
                    "目标已有 ${preview.memoryStateConflicts.size} 项状态。替换将保留目标原版本，并追加导入版本与来源。",
                color = palette.title, style = MaterialTheme.typography.bodyMedium
            )
            for (conflict in preview.memoryStateConflicts) Text(
                "${conflict.key.label} · ${conflict.targetSpaceId.take(16)} · 原版本 ${conflict.expectedRevision ?: "无"}",
                color = palette.body, style = MaterialTheme.typography.bodySmall
            )
            if (!multiple) TextButton(
                onClick = { onReplaceStates(!preview.replaceExistingStates) }, enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = NarraMinimumTouchTarget)
                    .testTag("memory_import_replace_states"),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.title),
            ) { Text(if (preview.replaceExistingStates) "已选择替换状态 · 点击取消" else "明确替换这些状态并保留历史") }
        }
    }
    selectingGroup?.let { groupKey ->
        NarraAlertDialog(
            title = "选择本机记忆目标",
            message = "请选择已存在的本机角色及身份。新建隔离档案只供历史管理，不自动进入当前对话。",
            onDismiss = { selectingGroup = null },
            onConfirm = { selectingGroup = null },
            confirmLabel = "关闭",
            dismissLabel = null,
            shape = NarraShapes.large,
            actionContentColor = palette.title,
        ) {
            if (preview.memoryTargetOptions.isEmpty()) Text(
                "没有可选角色。请先通过角色包入口恢复角色，或创建角色后重新预览。",
                color = palette.body
            )
            for (option in preview.memoryTargetOptions) TextButton(
                onClick = { selectingGroup = null; onTargetSelected(groupKey, option.target) },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = NarraMinimumTouchTarget),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.title),
                contentPadding = PaddingValues(
                    horizontal = NarraUi.space12,
                    vertical = NarraUi.space8
                ),
            ) { Text(option.label) }
        }
    }
}
