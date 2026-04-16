package com.example.myapplication.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.GiftImageStatus
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.formatTransferAmount
import com.example.myapplication.model.giftImageErrorMessage
import com.example.myapplication.model.giftImageFileName
import com.example.myapplication.model.giftImageMimeType
import com.example.myapplication.model.giftImageStatus
import com.example.myapplication.model.giftImageUri
import com.example.myapplication.model.hasGiftGeneratedImage
import com.example.myapplication.model.isSpecialPlayPart
import com.example.myapplication.model.isTransferPart
import com.example.myapplication.model.punishIntensityLabel
import com.example.myapplication.model.specialMetadataValue
import com.example.myapplication.model.specialPlayTitle
import com.example.myapplication.model.transferDirectionLabel
import com.example.myapplication.model.transferStatusLabel
import java.io.File

private data class SpecialPlayPalette(
    val topColor: Color,
    val bottomColor: Color,
    val emphasisColor: Color,
)

@Composable
fun SpecialPlayCard(
    part: ChatMessagePart,
    isUserMessage: Boolean,
    onConfirmTransferReceipt: ((String) -> Unit)?,
    autoPreviewImages: Boolean = true,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (!part.isSpecialPlayPart()) {
        return
    }

    // 转账使用独立的微信风格卡片
    if (part.specialType == ChatSpecialType.TRANSFER) {
        WeChatTransferCard(
            part = part,
            isUserMessage = isUserMessage,
            onConfirmTransferReceipt = onConfirmTransferReceipt,
            reduceMotion = reduceMotion,
            modifier = modifier,
        )
        return
    }

    var emphasized by remember(part.specialId) { mutableStateOf(reduceMotion) }
    LaunchedEffect(part.specialId, reduceMotion) {
        emphasized = reduceMotion
        if (!reduceMotion) {
            emphasized = true
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (reduceMotion) 1f else if (emphasized) 1f else 0.97f,
        animationSpec = tween(durationMillis = 220),
        label = "special_play_card_scale",
    )
    val palette = remember(part.specialType, isUserMessage) {
        resolveSpecialPlayPalette(part.specialType, isUserMessage)
    }
    val compactCard = part.specialType != ChatSpecialType.GIFT

    Surface(
        modifier = modifier
            .scale(scale)
            .widthIn(max = 280.dp),
        shape = RoundedCornerShape(if (compactCard) 16.dp else 20.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(palette.topColor, palette.bottomColor),
                    ),
                    shape = RoundedCornerShape(if (compactCard) 16.dp else 20.dp),
                )
                .let { baseModifier ->
                    if (reduceMotion) {
                        baseModifier
                    } else {
                        baseModifier.animateContentSize()
                    }
                }
                .padding(
                    horizontal = if (compactCard) 12.dp else 14.dp,
                    vertical = if (compactCard) 9.dp else 14.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (compactCard) 6.dp else 8.dp),
        ) {
            SpecialPlayHeader(
                icon = resolveSpecialPlayIcon(part.specialType),
                title = part.specialPlayTitle(),
                compact = compactCard,
            )

            when (part.specialType) {
                ChatSpecialType.INVITE -> InvitePlayBody(part = part, compact = compactCard)
                ChatSpecialType.GIFT -> GiftPlayBody(
                    part = part,
                    autoPreviewImages = autoPreviewImages,
                )
                ChatSpecialType.TASK -> TaskPlayBody(part = part, compact = compactCard)
                ChatSpecialType.PUNISH -> PunishPlayBody(part = part, compact = compactCard)
                ChatSpecialType.TRANSFER, null -> Unit
            }
        }
    }
}

/** 微信风格转账卡片：固定宽度、橙色主体 + 灰色底栏 */
@Composable
private fun WeChatTransferCard(
    part: ChatMessagePart,
    isUserMessage: Boolean,
    onConfirmTransferReceipt: ((String) -> Unit)?,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    var emphasized by remember(part.specialId) { mutableStateOf(reduceMotion) }
    LaunchedEffect(part.specialId, reduceMotion) {
        emphasized = reduceMotion
        if (!reduceMotion) {
            emphasized = true
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (reduceMotion) 1f else if (emphasized) 1f else 0.97f,
        animationSpec = tween(durationMillis = 220),
        label = "transfer_card_scale",
    )

    val isAssistantToUser = part.specialDirection == TransferDirection.ASSISTANT_TO_USER
    val canConfirmReceipt = !isUserMessage &&
        isAssistantToUser &&
        part.specialStatus == TransferStatus.PENDING &&
        onConfirmTransferReceipt != null &&
        part.specialId.isNotBlank()

    val cardShape = RoundedCornerShape(12.dp)
    val isDark = isSystemInDarkTheme()
    val footerBackground = if (isDark) Color(0xFF2C2C2C) else Color(0xFFF5F5F5)
    val footerTextColor = if (isDark) Color(0xFF888888) else Color(0xFF999999)

    Surface(
        modifier = modifier
            .scale(scale)
            .widthIn(min = 200.dp, max = 250.dp),
        shape = cardShape,
        color = Color.Transparent,
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 主体区域：橙色背景
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFF9A825), Color(0xFFF57F17)),
                        ),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 标题行
                Text(
                    text = part.specialPlayTitle().ifBlank { "转账" },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.88f),
                )
                // 金额
                Text(
                    text = part.formatTransferAmount(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                // 备注
                if (part.specialNote.isNotBlank()) {
                    Text(
                        text = part.specialNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                    )
                }
            }
            // 底栏：状态区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = footerBackground,
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = part.transferStatusLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = footerTextColor,
                )
                if (canConfirmReceipt) {
                    NarraFilledTonalButton(
                        onClick = { onConfirmTransferReceipt?.invoke(part.specialId) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFF9A825),
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Text("确认收款", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TransferPlayCard(
    part: ChatMessagePart,
    isUserMessage: Boolean,
    onConfirmTransferReceipt: ((String) -> Unit)?,
    autoPreviewImages: Boolean = true,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    SpecialPlayCard(
        part = part,
        isUserMessage = isUserMessage,
        onConfirmTransferReceipt = onConfirmTransferReceipt,
        autoPreviewImages = autoPreviewImages,
        reduceMotion = reduceMotion,
        modifier = modifier,
    )
}

@Composable
private fun SpecialPlayHeader(
    icon: ImageVector,
    title: String,
    compact: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 26.dp else 32.dp)
                .background(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 13.dp else 16.dp),
                tint = Color.White,
            )
        }
        Text(
            text = title,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InvitePlayBody(
    part: ChatMessagePart,
    compact: Boolean,
) {
    HighlightBlock(
        headline = part.specialMetadataValue("place").ifBlank { "待定地点" },
        supporting = part.specialMetadataValue("time").ifBlank { "待定时间" },
        compact = compact,
    )
    part.specialMetadataValue("note").takeIf { it.isNotBlank() }?.let { note ->
        Text(
            text = note,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
    if (!compact) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
        Text(
            text = "等你赴约",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun GiftPlayBody(
    part: ChatMessagePart,
    autoPreviewImages: Boolean,
) {
    HighlightBlock(
        headline = part.specialMetadataValue("item").ifBlank { "未命名礼物" },
        supporting = "对象：${part.specialMetadataValue("target").ifBlank { "对方" }}",
    )
    GiftImagePanel(
        part = part,
        autoPreviewImages = autoPreviewImages,
    )
    part.specialMetadataValue("note").takeIf { it.isNotBlank() }?.let { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
    Text(
        text = when (part.giftImageStatus()) {
            GiftImageStatus.GENERATING -> "礼物图生成中"
            GiftImageStatus.SUCCEEDED -> "礼物图已就绪"
            GiftImageStatus.FAILED -> "礼物图生成失败"
            null -> "已准备交付"
        },
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.88f),
    )
}

@Composable
private fun GiftImagePanel(
    part: ChatMessagePart,
    autoPreviewImages: Boolean,
) {
    val status = part.giftImageStatus() ?: return
    val shape = RoundedCornerShape(18.dp)
    val imageUri = part.giftImageUri()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f),
        shape = shape,
        color = Color.White.copy(alpha = 0.14f),
    ) {
        when {
            status == GiftImageStatus.GENERATING -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.4.dp,
                    )
                    Text(
                        text = "正在生成礼物图",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = "图片生成完成后会直接挂在这张礼物卡上",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            status == GiftImageStatus.SUCCEEDED && imageUri.isNotBlank() && autoPreviewImages -> {
                var loadFailed by remember(imageUri) { mutableStateOf(false) }
                AsyncImage(
                    model = resolveSpecialPlayImageModel(imageUri),
                    contentDescription = part.giftImageFileName().ifBlank { "礼物图" },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                    contentScale = ContentScale.Crop,
                    onLoading = { loadFailed = false },
                    onSuccess = { loadFailed = false },
                    onError = { loadFailed = true },
                )
                if (loadFailed) {
                    GiftImageStateOverlay(
                        title = "礼物图暂时不可预览",
                        body = "图片文件无法读取，但礼物卡已经保存完成。",
                    )
                }
            }

            status == GiftImageStatus.SUCCEEDED && imageUri.isNotBlank() && !autoPreviewImages -> {
                GiftImageStateOverlay(
                    title = part.giftImageFileName().ifBlank { "礼物图已生成" },
                    body = "已关闭自动图片预览，可在打开图片预览后查看。",
                )
            }

            status == GiftImageStatus.FAILED -> {
                GiftImageStateOverlay(
                    title = "礼物图生成失败",
                    body = part.giftImageErrorMessage().ifBlank { "稍后可以重新发送礼物卡再试。" },
                )
            }

            else -> {
                GiftImageStateOverlay(
                    title = "礼物图暂不可用",
                    body = "当前礼物卡尚未拿到可显示的图片。",
                )
            }
        }
    }
}

@Composable
private fun GiftImageStateOverlay(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.82f),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TaskPlayBody(
    part: ChatMessagePart,
    compact: Boolean,
) {
    HighlightBlock(
        headline = part.specialMetadataValue("title").ifBlank { "新的委托" },
        supporting = part.specialMetadataValue("objective").ifBlank { "暂无目标说明" },
        compact = compact,
    )
    part.specialMetadataValue("reward").takeIf { it.isNotBlank() }?.let { reward ->
        Text(
            text = "奖励：$reward",
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
    part.specialMetadataValue("deadline").takeIf { it.isNotBlank() }?.let { deadline ->
        Text(
            text = "期限：$deadline",
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
}

@Composable
private fun PunishPlayBody(
    part: ChatMessagePart,
    compact: Boolean,
) {
    HighlightBlock(
        headline = part.specialMetadataValue("method").ifBlank { "待定方式" },
        supporting = "强度 ${part.punishIntensityLabel()} · 次数 ${part.specialMetadataValue("count").ifBlank { "一下" }}",
        compact = compact,
    )
    part.specialMetadataValue("reason").takeIf { it.isNotBlank() }?.let { reason ->
        Text(
            text = "原因：$reason",
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
    part.specialMetadataValue("note").takeIf { it.isNotBlank() }?.let { note ->
        Text(
            text = "附注：$note",
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
    if (!compact) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
        Text(
            text = "等待对方反应",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun HighlightBlock(
    headline: String,
    supporting: String,
    compact: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)) {
        Text(
            text = headline,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = supporting,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
}

private fun resolveSpecialPlayIcon(type: ChatSpecialType?): ImageVector {
    return when (type) {
        ChatSpecialType.TRANSFER -> Icons.Default.AccountBalanceWallet
        ChatSpecialType.INVITE -> Icons.Default.Event
        ChatSpecialType.GIFT -> Icons.Default.CardGiftcard
        ChatSpecialType.TASK -> Icons.AutoMirrored.Filled.Assignment
        ChatSpecialType.PUNISH -> Icons.Default.Gavel
        null -> Icons.Default.Share
    }
}

private fun resolveSpecialPlayPalette(
    type: ChatSpecialType?,
    isUserMessage: Boolean,
): SpecialPlayPalette {
    return when (type) {
        ChatSpecialType.TRANSFER -> {
            if (isUserMessage) {
                SpecialPlayPalette(
                    topColor = Color(0xFFF4C16F),
                    bottomColor = Color(0xFFEAAA47),
                    emphasisColor = Color(0xFF8A4B08),
                )
            } else {
                SpecialPlayPalette(
                    topColor = Color(0xFFF0B35A),
                    bottomColor = Color(0xFFE19A3A),
                    emphasisColor = Color(0xFF8A4B08),
                )
            }
        }

        ChatSpecialType.INVITE -> SpecialPlayPalette(
            topColor = if (isUserMessage) Color(0xFF75AEEF) else Color(0xFF5A97E8),
            bottomColor = if (isUserMessage) Color(0xFF4E8EE8) else Color(0xFF3474D6),
            emphasisColor = Color(0xFF1D4A89),
        )

        ChatSpecialType.GIFT -> SpecialPlayPalette(
            topColor = if (isUserMessage) Color(0xFFF17B88) else Color(0xFFE96374),
            bottomColor = if (isUserMessage) Color(0xFFD85768) else Color(0xFFC94859),
            emphasisColor = Color(0xFF8F2433),
        )

        ChatSpecialType.TASK -> SpecialPlayPalette(
            topColor = if (isUserMessage) Color(0xFFF1B86A) else Color(0xFFE7A44D),
            bottomColor = if (isUserMessage) Color(0xFFDB9041) else Color(0xFFC87624),
            emphasisColor = Color(0xFF7D470F),
        )

        ChatSpecialType.PUNISH -> SpecialPlayPalette(
            topColor = if (isUserMessage) Color(0xFFEC7285) else Color(0xFFE45A71),
            bottomColor = if (isUserMessage) Color(0xFFD45066) else Color(0xFFB93B51),
            emphasisColor = Color(0xFF7B1E31),
        )

        null -> SpecialPlayPalette(
            topColor = Color(0xFF8696B5),
            bottomColor = Color(0xFF637394),
            emphasisColor = Color(0xFF32415F),
        )
    }
}

private fun resolveSpecialPlayImageModel(uri: String): Any {
    return when {
        uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true) ||
            uri.startsWith("data:image", ignoreCase = true) ||
            uri.startsWith("file://", ignoreCase = true) -> uri
        uri.startsWith("content://", ignoreCase = true) -> uri.toUri()
        else -> File(uri)
    }
}
