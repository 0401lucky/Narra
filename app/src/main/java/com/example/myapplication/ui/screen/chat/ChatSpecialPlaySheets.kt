@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.InvitePlayDraft
import com.example.myapplication.model.PunishPlayDraft
import com.example.myapplication.model.TaskPlayDraft
import com.example.myapplication.model.TransferPlayDraft
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.SpecialPlayCard

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
    val phoneCheck = phoneCheckOption()
    val videoCall = if (onOpenVideoCall != null) videoCallOption() else null
    val moments = if (onOpenMoments != null) momentsOption() else null
    val standaloneEntries = buildList {
        add(
            SpecialPlayModuleEntry(
                option = phoneCheck,
                onClick = onOpenPhoneCheck,
            ),
        )
        onOpenVideoCall?.let { openVideoCall ->
            add(
                SpecialPlayModuleEntry(
                    option = videoCall!!,
                    onClick = openVideoCall,
                ),
            )
        }
        onOpenMoments?.let { openMoments ->
            add(
                SpecialPlayModuleEntry(
                    option = moments!!,
                    onClick = openMoments,
                ),
            )
        }
    }
    val socialEntries = specialPlayOptions()
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
    val storyEntries = specialPlayOptions()
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
                    color = MaterialTheme.colorScheme.surface,
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
                    color = MaterialTheme.colorScheme.surface,
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
        color = MaterialTheme.colorScheme.surface,
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
