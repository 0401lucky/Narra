@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.InvitePlayDraft
import com.example.myapplication.model.TaskPlayDraft
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferPlayDraft
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.giftMessagePart
import com.example.myapplication.model.inviteMessagePart
import com.example.myapplication.model.taskMessagePart
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.SpecialPlayCard

private data class SpecialPlayOption(
    val type: ChatSpecialType,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBackground: Color,
)

private val TransferGreen = Color(0xFF07C160)
private val TransferGreenSoft = Color(0xFFE9F8EF)
private val InviteBlue = Color(0xFF4D86E8)
private val InviteBlueSoft = Color(0xFFEAF1FF)
private val GiftRose = Color(0xFFE46074)
private val GiftRoseSoft = Color(0xFFFFEEF1)
private val TaskAmber = Color(0xFFD78B31)
private val TaskAmberSoft = Color(0xFFFFF2E1)

private val specialPlayOptions = listOf(
    SpecialPlayOption(
        type = ChatSpecialType.TRANSFER,
        title = "转账",
        description = "模拟收发转账，适合红包、垫付和情绪互动。",
        icon = Icons.Default.Share,
        iconTint = TransferGreen,
        iconBackground = TransferGreenSoft,
    ),
    SpecialPlayOption(
        type = ChatSpecialType.INVITE,
        title = "邀约",
        description = "把见面、约会和碰头做成可见卡片。",
        icon = Icons.Default.Event,
        iconTint = InviteBlue,
        iconBackground = InviteBlueSoft,
    ),
    SpecialPlayOption(
        type = ChatSpecialType.GIFT,
        title = "礼物",
        description = "把赠送物品、纪念品和惊喜变成具象互动。",
        icon = Icons.Default.CardGiftcard,
        iconTint = GiftRose,
        iconBackground = GiftRoseSoft,
    ),
    SpecialPlayOption(
        type = ChatSpecialType.TASK,
        title = "委托",
        description = "适合剧情线索、目标推进和阶段任务。",
        icon = Icons.AutoMirrored.Filled.Assignment,
        iconTint = TaskAmber,
        iconBackground = TaskAmberSoft,
    ),
)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialPlaySheet(
    onDismissRequest: () -> Unit,
    onOpenPlay: (ChatSpecialType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "特殊玩法",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "把社交互动和剧情推进变成可见卡片。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SpecialPlayGroup(title = "社交互动") {
                specialPlayOptions
                    .filter { it.type in setOf(ChatSpecialType.TRANSFER, ChatSpecialType.INVITE, ChatSpecialType.GIFT) }
                    .forEach { option ->
                        SpecialPlayEntry(option = option, onClick = { onOpenPlay(option.type) })
                    }
            }

            SpecialPlayGroup(title = "剧情推进") {
                specialPlayOptions
                    .filter { it.type == ChatSpecialType.TASK }
                    .forEach { option ->
                        SpecialPlayEntry(option = option, onClick = { onOpenPlay(option.type) })
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialPlayEditorSheet(
    draft: ChatSpecialPlayDraft,
    onDraftChange: (ChatSpecialPlayDraft) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val option = rememberOption(draft.type)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = option.iconBackground,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    color = option.iconTint.copy(alpha = 0.14f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = option.iconTint,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = when (draft.type) {
                                    ChatSpecialType.TRANSFER -> "发起一笔转账"
                                    ChatSpecialType.INVITE -> "发出一份邀约"
                                    ChatSpecialType.GIFT -> "准备一份礼物"
                                    ChatSpecialType.TASK -> "创建一个委托"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    color = Color.White,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (draft) {
                            is TransferPlayDraft -> TransferDraftFields(
                                draft = draft,
                                option = option,
                                onDraftChange = { onDraftChange(it) },
                            )

                            is InvitePlayDraft -> InviteDraftFields(
                                draft = draft,
                                option = option,
                                onDraftChange = { onDraftChange(it) },
                            )

                            is GiftPlayDraft -> GiftDraftFields(
                                draft = draft,
                                option = option,
                                onDraftChange = { onDraftChange(it) },
                            )

                            is TaskPlayDraft -> TaskDraftFields(
                                draft = draft,
                                option = option,
                                onDraftChange = { onDraftChange(it) },
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "发送后预览",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SpecialPlayCard(
                            part = buildPreviewPart(draft),
                            isUserMessage = true,
                            onConfirmTransferReceipt = null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                NarraButton(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canConfirmDraft(draft),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = option.iconTint,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    contentPadding = PaddingValues(vertical = 15.dp),
                ) {
                    Text(
                        text = "发送${option.title}卡片",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            item {
                NarraTextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun SpecialPlayGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun SpecialPlayEntry(
    option: SpecialPlayOption,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        color = option.iconBackground,
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = option.iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransferDraftFields(
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
private fun InviteDraftFields(
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
private fun GiftDraftFields(
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
private fun TaskDraftFields(
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

private fun rememberOption(type: ChatSpecialType): SpecialPlayOption {
    return specialPlayOptions.first { it.type == type }
}

private fun canConfirmDraft(draft: ChatSpecialPlayDraft): Boolean {
    return when (draft) {
        is TransferPlayDraft -> draft.amount.trim().isNotBlank()
        is InvitePlayDraft -> draft.place.trim().isNotBlank() && draft.time.trim().isNotBlank()
        is GiftPlayDraft -> draft.item.trim().isNotBlank()
        is TaskPlayDraft -> draft.title.trim().isNotBlank() && draft.objective.trim().isNotBlank()
    }
}

private fun buildPreviewPart(draft: ChatSpecialPlayDraft) = when (draft) {
    is TransferPlayDraft -> transferMessagePart(
        direction = TransferDirection.USER_TO_ASSISTANT,
        status = TransferStatus.PENDING,
        counterparty = draft.counterparty.ifBlank { "对方" },
        amount = draft.amount.ifBlank { "0.00" },
        note = draft.note,
    )

    is InvitePlayDraft -> inviteMessagePart(
        target = draft.target.ifBlank { "对方" },
        place = draft.place.ifBlank { "待定地点" },
        time = draft.time.ifBlank { "待定时间" },
        note = draft.note,
    )

    is GiftPlayDraft -> giftMessagePart(
        target = draft.target.ifBlank { "对方" },
        item = draft.item.ifBlank { "未命名礼物" },
        note = draft.note,
    )

    is TaskPlayDraft -> taskMessagePart(
        title = draft.title.ifBlank { "新的委托" },
        objective = draft.objective.ifBlank { "暂无目标说明" },
        reward = draft.reward,
        deadline = draft.deadline,
    )
}
