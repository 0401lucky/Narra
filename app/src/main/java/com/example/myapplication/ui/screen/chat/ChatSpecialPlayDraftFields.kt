@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.InvitePlayDraft
import com.example.myapplication.model.PunishIntensity
import com.example.myapplication.model.PunishPlayDraft
import com.example.myapplication.model.TaskPlayDraft
import com.example.myapplication.model.TransferPlayDraft

val TransferPlayDraftSaver: Saver<TransferPlayDraft, Any> = listSaver(
    save = { listOf(it.counterparty, it.amount, it.note) },
    restore = {
        TransferPlayDraft(
            counterparty = (it.getOrNull(0) as? String).orEmpty(),
            amount = (it.getOrNull(1) as? String).orEmpty(),
            note = (it.getOrNull(2) as? String).orEmpty(),
        )
    },
)

val InvitePlayDraftSaver: Saver<InvitePlayDraft, Any> = listSaver(
    save = { listOf(it.target, it.place, it.time, it.note) },
    restore = {
        InvitePlayDraft(
            target = (it.getOrNull(0) as? String).orEmpty(),
            place = (it.getOrNull(1) as? String).orEmpty(),
            time = (it.getOrNull(2) as? String).orEmpty(),
            note = (it.getOrNull(3) as? String).orEmpty(),
        )
    },
)

val GiftPlayDraftSaver: Saver<GiftPlayDraft, Any> = listSaver(
    save = { listOf(it.target, it.item, it.note) },
    restore = {
        GiftPlayDraft(
            target = (it.getOrNull(0) as? String).orEmpty(),
            item = (it.getOrNull(1) as? String).orEmpty(),
            note = (it.getOrNull(2) as? String).orEmpty(),
        )
    },
)

val TaskPlayDraftSaver: Saver<TaskPlayDraft, Any> = listSaver(
    save = { listOf(it.title, it.objective, it.reward, it.deadline) },
    restore = {
        TaskPlayDraft(
            title = (it.getOrNull(0) as? String).orEmpty(),
            objective = (it.getOrNull(1) as? String).orEmpty(),
            reward = (it.getOrNull(2) as? String).orEmpty(),
            deadline = (it.getOrNull(3) as? String).orEmpty(),
        )
    },
)

val PunishPlayDraftSaver: Saver<PunishPlayDraft, Any> = listSaver(
    save = {
        listOf(
            it.method,
            it.count,
            it.intensity.storageValue,
            it.reason,
            it.note,
        )
    },
    restore = {
        PunishPlayDraft(
            method = (it.getOrNull(0) as? String).orEmpty(),
            count = (it.getOrNull(1) as? String).orEmpty(),
            intensity = PunishIntensity.fromStorageValue((it.getOrNull(2) as? String).orEmpty())
                ?: PunishIntensity.MEDIUM,
            reason = (it.getOrNull(3) as? String).orEmpty(),
            note = (it.getOrNull(4) as? String).orEmpty(),
        )
    },
)

@Composable
internal fun TransferDraftFields(
    draft: TransferPlayDraft,
    option: SpecialPlayOption,
    onDraftChange: (TransferPlayDraft) -> Unit,
) {
    SpecialLineField(
        label = "收款对象",
        value = draft.counterparty,
        placeholder = "例如：陆宴清",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(counterparty = it)) },
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "转账金额",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "¥",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            BasicTextField(
                value = draft.amount,
                onValueChange = {
                    onDraftChange(
                        draft.copy(
                            amount = it.filter { ch -> ch.isDigit() || ch == '.' },
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(option.iconTint),
                decorationBox = { innerTextField ->
                    if (draft.amount.isBlank()) {
                        Text(
                            text = "0.00",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                        )
                    }
                    innerTextField()
                },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("52", "99", "520", "1314").forEach { amount ->
                FilterChip(
                    selected = draft.amount == amount,
                    onClick = { onDraftChange(draft.copy(amount = amount)) },
                    label = { Text("¥$amount") },
                )
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

    SpecialLineField(
        label = "备注",
        value = draft.note,
        placeholder = "例如：今天给你买奶茶",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(note = it)) },
    )
}

@Composable
internal fun InviteDraftFields(
    draft: InvitePlayDraft,
    option: SpecialPlayOption,
    onDraftChange: (InvitePlayDraft) -> Unit,
) {
    SpecialLineField(
        label = "邀约对象",
        value = draft.target,
        placeholder = "例如：陆宴清",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(target = it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    SpecialLineField(
        label = "地点",
        value = draft.place,
        placeholder = "例如：江边步道",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(place = it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    SpecialLineField(
        label = "时间",
        value = draft.time,
        placeholder = "例如：今晚九点",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(time = it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    SpecialLineField(
        label = "附言",
        value = draft.note,
        placeholder = "例如：这次别临时失约",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(note = it)) },
    )
}

@Composable
internal fun GiftDraftFields(
    draft: GiftPlayDraft,
    option: SpecialPlayOption,
    onDraftChange: (GiftPlayDraft) -> Unit,
) {
    SpecialLineField(
        label = "送礼对象",
        value = draft.target,
        placeholder = "例如：陆宴清",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(target = it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    SpecialLineField(
        label = "礼物内容",
        value = draft.item,
        placeholder = "例如：黑胶唱片",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(item = it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    SpecialLineField(
        label = "附言",
        value = draft.note,
        placeholder = "例如：我觉得你会喜欢这个",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(note = it)) },
    )
}

@Composable
internal fun TaskDraftFields(
    draft: TaskPlayDraft,
    option: SpecialPlayOption,
    onDraftChange: (TaskPlayDraft) -> Unit,
) {
    SpecialLineField(
        label = "委托标题",
        value = draft.title,
        placeholder = "例如：寻找钥匙",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(title = it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    SpecialLineField(
        label = "目标",
        value = draft.objective,
        placeholder = "例如：在旧图书馆找到铜钥匙",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(objective = it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    SpecialLineField(
        label = "奖励",
        value = draft.reward,
        placeholder = "例如：一个答案",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(reward = it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    SpecialLineField(
        label = "期限",
        value = draft.deadline,
        placeholder = "例如：天亮前",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(deadline = it)) },
    )
}

@Composable
internal fun PunishDraftFields(
    draft: PunishPlayDraft,
    option: SpecialPlayOption,
    onDraftChange: (PunishPlayDraft) -> Unit,
) {
    SpecialLineField(
        label = "方式",
        value = draft.method,
        placeholder = "例如：扇巴掌 / 鞭子 / 戒尺",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(method = it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    SpecialLineField(
        label = "次数",
        value = draft.count,
        placeholder = "例如：一下 / 三下",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(count = it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    PunishIntensityField(
        intensity = draft.intensity,
        onIntensityChange = { onDraftChange(draft.copy(intensity = it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    SpecialLineField(
        label = "原因",
        value = draft.reason,
        placeholder = "例如：为什么要罚",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(reason = it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    SpecialLineField(
        label = "附注",
        value = draft.note,
        placeholder = "例如：边抽边说的话",
        accentColor = option.iconTint,
        onValueChange = { onDraftChange(draft.copy(note = it)) },
    )
}

@Composable
private fun PunishIntensityField(
    intensity: PunishIntensity,
    onIntensityChange: (PunishIntensity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "强度",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PunishIntensity.entries.forEach { option ->
                FilterChip(
                    selected = intensity == option,
                    onClick = { onIntensityChange(option) },
                    label = { Text(option.displayName) },
                )
            }
        }
    }
}

@Composable
private fun SpecialLineField(
    label: String,
    value: String,
    placeholder: String,
    accentColor: Color,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(accentColor),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
