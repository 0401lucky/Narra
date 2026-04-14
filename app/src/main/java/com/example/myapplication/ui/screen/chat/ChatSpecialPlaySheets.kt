@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.example.myapplication.model.PunishIntensity
import com.example.myapplication.model.PunishPlayDraft
import com.example.myapplication.model.TaskPlayDraft
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferPlayDraft
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.giftMessagePart
import com.example.myapplication.model.inviteMessagePart
import com.example.myapplication.model.punishMessagePart
import com.example.myapplication.model.taskMessagePart
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.SpecialPlayCard
import com.example.myapplication.ui.theme.MomentsAccent
import com.example.myapplication.ui.theme.MomentsAccentSoft

private data class SpecialPlayOption(
    val type: ChatSpecialType,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBackground: Color,
)

private data class SpecialPlayModuleEntry(
    val option: SpecialPlayOption,
    val onClick: () -> Unit,
)

private val TransferGreen = Color(0xFF07C160)
private val TransferGreenSoft = Color(0xFFE9F8EF)
private val InviteBlue = Color(0xFF4D86E8)
private val InviteBlueSoft = Color(0xFFEAF1FF)
private val GiftRose = Color(0xFFE46074)
private val GiftRoseSoft = Color(0xFFFFEEF1)
private val TaskAmber = Color(0xFFD78B31)
private val TaskAmberSoft = Color(0xFFFFF2E1)
private val PunishCrimson = Color(0xFFD54F63)
private val PunishCrimsonSoft = Color(0xFFFFEEF1)
private val PhoneBlue = Color(0xFF4A86D9)
private val PhoneBlueSoft = Color(0xFFEAF2FF)

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
    SpecialPlayOption(
        type = ChatSpecialType.PUNISH,
        title = "惩罚",
        description = "把抽打、教训和压迫感做成结构化动作卡，方便角色直接接反应。",
        icon = Icons.Default.Gavel,
        iconTint = PunishCrimson,
        iconBackground = PunishCrimsonSoft,
    ),
)

private val phoneCheckOption = SpecialPlayOption(
    type = ChatSpecialType.TASK,
    title = "查手机",
    description = "进入独立手机页，浏览已经生成或保存下来的消息、备忘录、相册、购物和搜索内容。",
    icon = Icons.Default.Visibility,
    iconTint = PhoneBlue,
    iconBackground = PhoneBlueSoft,
)

private val videoCallOption = SpecialPlayOption(
    type = ChatSpecialType.INVITE,
    title = "视频通话",
    description = "进入独立视频通话页，保持同一条线上聊天会话，挂断后文本内容会回流到聊天里。",
    icon = Icons.Default.Videocam,
    iconTint = InviteBlue,
    iconBackground = InviteBlueSoft,
)

private val momentsOption = SpecialPlayOption(
    type = ChatSpecialType.INVITE,
    title = "动态",
    description = "浏览朋友圈动态，点赞评论后角色会自动回复互动。",
    icon = Icons.Default.Forum,
    iconTint = MomentsAccent,
    iconBackground = MomentsAccentSoft,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialPlaySheet(
    onDismissRequest: () -> Unit,
    onOpenPlay: (ChatSpecialType) -> Unit,
    onOpenPhoneCheck: () -> Unit,
    onOpenVideoCall: (() -> Unit)? = null,
    onOpenMoments: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val standaloneEntries = buildList {
        add(
            SpecialPlayModuleEntry(
                option = phoneCheckOption,
                onClick = onOpenPhoneCheck,
            ),
        )
        onOpenVideoCall?.let { openVideoCall ->
            add(
                SpecialPlayModuleEntry(
                    option = videoCallOption,
                    onClick = openVideoCall,
                ),
            )
        }
        onOpenMoments?.let { openMoments ->
            add(
                SpecialPlayModuleEntry(
                    option = momentsOption,
                    onClick = openMoments,
                ),
            )
        }
    }
    val socialEntries = specialPlayOptions
        .filter {
            it.type in setOf(
                ChatSpecialType.TRANSFER,
                ChatSpecialType.INVITE,
                ChatSpecialType.GIFT,
                ChatSpecialType.PUNISH,
            )
        }
        .map { option ->
            SpecialPlayModuleEntry(
                option = option,
                onClick = { onOpenPlay(option.type) },
            )
        }
    val storyEntries = specialPlayOptions
        .filter { it.type == ChatSpecialType.TASK }
        .map { option ->
            SpecialPlayModuleEntry(
                option = option,
                onClick = { onOpenPlay(option.type) },
            )
        }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                text = "既可以发送卡片玩法，也可以直接进入独立页面玩法。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SpecialPlayGroup(
                title = "独立页面",
                subtitle = "直接进入完整玩法页，更适合连续操作和浏览。",
                entries = standaloneEntries,
            )
            SpecialPlayGroup(
                title = "社交互动",
                subtitle = "先编辑，再发送结构化互动卡片。",
                entries = socialEntries,
            )
            SpecialPlayGroup(
                title = "剧情推进",
                subtitle = "把目标、委托和阶段推进单独做成模块。",
                entries = storyEntries,
            )
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
                                    ChatSpecialType.PUNISH -> "发起一次惩罚"
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

                            is PunishPlayDraft -> PunishDraftFields(
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
    subtitle: String,
    entries: List<SpecialPlayModuleEntry>,
) {
    if (entries.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entries.chunked(4).forEach { rowEntries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowEntries.forEach { entry ->
                    SpecialPlayEntry(
                        entry = entry,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - rowEntries.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SpecialPlayEntry(
    entry: SpecialPlayModuleEntry,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        onClick = entry.onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 118.dp)
                .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = entry.option.iconTint,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = entry.option.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = entry.option.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
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
private fun PunishDraftFields(
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

private fun rememberOption(type: ChatSpecialType): SpecialPlayOption {
    return specialPlayOptions.first { it.type == type }
}

private fun canConfirmDraft(draft: ChatSpecialPlayDraft): Boolean {
    return when (draft) {
        is TransferPlayDraft -> draft.amount.trim().isNotBlank()
        is InvitePlayDraft -> draft.place.trim().isNotBlank() && draft.time.trim().isNotBlank()
        is GiftPlayDraft -> draft.item.trim().isNotBlank()
        is TaskPlayDraft -> draft.title.trim().isNotBlank() && draft.objective.trim().isNotBlank()
        is PunishPlayDraft -> draft.method.trim().isNotBlank() && draft.count.trim().isNotBlank()
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

    is PunishPlayDraft -> punishMessagePart(
        method = draft.method.ifBlank { "待定方式" },
        count = draft.count.ifBlank { "一下" },
        intensity = draft.intensity,
        reason = draft.reason,
        note = draft.note,
    )
}
