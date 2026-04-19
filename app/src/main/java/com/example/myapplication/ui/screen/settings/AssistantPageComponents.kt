package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.Assistant
import com.example.myapplication.ui.component.AssistantAvatar

/**
 * 助手子页（基础 / 提示词 / 扩展 / 记忆）顶部的薄 header。
 * 只保留小头像 + 助手名 + 小标签，避免与外层 SettingsTopBar 的标题重复。
 * 顶栏已显示"基础设定"等页面名，这里只承担"我在谁的配置里"的上下文。
 */
@Composable
internal fun AssistantSubPageHeader(
    assistant: Assistant,
    overline: String,
) {
    val palette = rememberSettingsPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AssistantAvatar(
            name = assistant.name,
            iconName = assistant.iconName,
            avatarUri = assistant.avatarUri,
            size = 40.dp,
            containerColor = palette.subtleChip,
            contentColor = palette.subtleChipContent,
            cornerRadius = 12.dp,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = overline,
                style = MaterialTheme.typography.labelMedium,
                color = palette.accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = assistant.name.ifBlank { "未命名助手" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.title,
            )
        }
    }
}

@Composable
internal fun AssistantSubsectionTitle(
    title: String,
    subtitle: String = "",
) {
    val palette = rememberSettingsPalette()
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
