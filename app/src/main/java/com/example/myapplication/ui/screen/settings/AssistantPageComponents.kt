package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.Assistant
import com.example.myapplication.ui.component.AssistantAvatar

/**
 * 助手子页（基础 / 提示词 / 扩展 / 记忆）顶部的玻璃拟态 hero。
 * 当前走"渐变底色 + 半透明填充 + 边缘柔光 + 圆角 28"的现代玻璃感，
 * 避免与外层 SettingsTopBar 的标题重复，聚焦"我在谁的配置里"。
 */
@Composable
internal fun AssistantSubPageHeader(
    assistant: Assistant,
    overline: String,
) {
    val palette = rememberSettingsPalette()
    val gradient = Brush.linearGradient(
        colors = listOf(
            palette.accent.copy(alpha = 0.18f),
            palette.accentSoft.copy(alpha = 0.10f),
            palette.surface.copy(alpha = 0.95f),
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(gradient)
            .border(
                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.22f)),
                shape = RoundedCornerShape(26.dp),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AssistantAvatar(
                name = assistant.name,
                iconName = assistant.iconName,
                avatarUri = assistant.avatarUri,
                size = 48.dp,
                containerColor = palette.accentSoft.copy(alpha = 0.45f),
                contentColor = palette.accent,
                cornerRadius = 16.dp,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = overline,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = assistant.name.ifBlank { "未命名助手" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 子页内的章节标题：左侧用 accent 色的细柱装饰代替单纯的字号层级，
 * 比旧版更容易一眼辨识"这是一个新章节"。
 */
@Composable
internal fun AssistantSubsectionTitle(
    title: String,
    subtitle: String = "",
) {
    val palette = rememberSettingsPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.accent,
                            palette.accent.copy(alpha = 0.55f),
                        ),
                    ),
                ),
        )
        Spacer(Modifier.width(10.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.title,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.body,
                )
            }
        }
    }
}

/**
 * 极简装饰 divider：在章节之间铺一条淡淡的渐变线，增强"翻篇感"。
 * 在 LazyColumn 里作为独立 item 使用。
 */
@Composable
internal fun AssistantSectionDivider() {
    val palette = rememberSettingsPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.accent.copy(alpha = 0.25f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}
