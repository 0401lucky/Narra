package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.InvitePlayDraft
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
import com.example.myapplication.ui.theme.MomentsAccent
import com.example.myapplication.ui.theme.MomentsAccentSoft

internal data class SpecialPlayOption(
    val type: ChatSpecialType,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBackground: Color,
)

internal data class SpecialPlayModuleEntry(
    val option: SpecialPlayOption,
    val onClick: () -> Unit,
)

internal val TransferGreen = Color(0xFF07C160)
internal val InviteBlue = Color(0xFF4D86E8)
internal val GiftRose = Color(0xFFE46074)
internal val TaskAmber = Color(0xFFD78B31)
internal val PunishCrimson = Color(0xFFD54F63)
internal val PhoneBlue = Color(0xFF4A86D9)

@Composable
internal fun specialPlayOptions(): List<SpecialPlayOption> {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        listOf(
            SpecialPlayOption(
                type = ChatSpecialType.TRANSFER,
                title = "转账",
                description = "模拟收发转账，适合红包、垫付和情绪互动。",
                icon = Icons.Default.Share,
                iconTint = TransferGreen,
                iconBackground = if (dark) Color(0xFF1A2E22) else Color(0xFFE9F8EF),
            ),
            SpecialPlayOption(
                type = ChatSpecialType.INVITE,
                title = "邀约",
                description = "把见面、约会和碰头做成可见卡片。",
                icon = Icons.Default.Event,
                iconTint = InviteBlue,
                iconBackground = if (dark) Color(0xFF1E2840) else Color(0xFFEAF1FF),
            ),
            SpecialPlayOption(
                type = ChatSpecialType.GIFT,
                title = "礼物",
                description = "把赠送物品、纪念品和惊喜变成具象互动。",
                icon = Icons.Default.CardGiftcard,
                iconTint = GiftRose,
                iconBackground = if (dark) Color(0xFF3A1E24) else Color(0xFFFFEEF1),
            ),
            SpecialPlayOption(
                type = ChatSpecialType.TASK,
                title = "委托",
                description = "适合剧情线索、目标推进和阶段任务。",
                icon = Icons.AutoMirrored.Filled.Assignment,
                iconTint = TaskAmber,
                iconBackground = if (dark) Color(0xFF332614) else Color(0xFFFFF2E1),
            ),
            SpecialPlayOption(
                type = ChatSpecialType.PUNISH,
                title = "惩罚",
                description = "把抽打、教训和压迫感做成结构化动作卡，方便角色直接接反应。",
                icon = Icons.Default.Gavel,
                iconTint = PunishCrimson,
                iconBackground = if (dark) Color(0xFF3A1E24) else Color(0xFFFFEEF1),
            ),
        )
    }
}

@Composable
internal fun phoneCheckOption(): SpecialPlayOption {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        SpecialPlayOption(
            type = ChatSpecialType.TASK,
            title = "查手机",
            description = "进入独立手机页，浏览已经生成或保存下来的消息、备忘录、相册、购物和搜索内容。",
            icon = Icons.Default.Visibility,
            iconTint = PhoneBlue,
            iconBackground = if (dark) Color(0xFF1E2A3E) else Color(0xFFEAF2FF),
        )
    }
}

@Composable
internal fun videoCallOption(): SpecialPlayOption {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        SpecialPlayOption(
            type = ChatSpecialType.INVITE,
            title = "视频通话",
            description = "进入独立视频通话页，保持同一条线上聊天会话，挂断后文本内容会回流到聊天里。",
            icon = Icons.Default.Videocam,
            iconTint = InviteBlue,
            iconBackground = if (dark) Color(0xFF1E2840) else Color(0xFFEAF1FF),
        )
    }
}

@Composable
internal fun momentsOption(): SpecialPlayOption {
    return SpecialPlayOption(
        type = ChatSpecialType.INVITE,
        title = "动态",
        description = "浏览朋友圈动态，点赞评论后角色会自动回复互动。",
        icon = Icons.Default.Forum,
        iconTint = MomentsAccent(),
        iconBackground = MomentsAccentSoft(),
    )
}

@Composable
internal fun rememberOption(type: ChatSpecialType): SpecialPlayOption {
    return specialPlayOptions().first { it.type == type }
}

internal fun canConfirmDraft(draft: ChatSpecialPlayDraft): Boolean {
    return when (draft) {
        is TransferPlayDraft -> draft.amount.trim().isNotBlank()
        is InvitePlayDraft -> draft.place.trim().isNotBlank() && draft.time.trim().isNotBlank()
        is GiftPlayDraft -> draft.item.trim().isNotBlank()
        is TaskPlayDraft -> draft.title.trim().isNotBlank() && draft.objective.trim().isNotBlank()
        is PunishPlayDraft -> draft.method.trim().isNotBlank() && draft.count.trim().isNotBlank()
    }
}

internal fun buildPreviewPart(draft: ChatSpecialPlayDraft) = when (draft) {
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
