package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.Assistant
import com.example.myapplication.ui.component.AssistantAvatar

@Composable
internal fun AssistantSubPageHeader(
    assistant: Assistant,
    overline: String,
) {
    val palette = rememberSettingsPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AssistantAvatar(
            name = assistant.name,
            iconName = assistant.iconName,
            avatarUri = assistant.avatarUri,
            size = 48.dp,
            containerColor = palette.accentSoft,
            contentColor = palette.accent,
            cornerRadius = 24.dp,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = assistant.name.ifBlank { "未命名角色" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = overline,
                style = MaterialTheme.typography.bodySmall,
                color = palette.body,
            )
        }
    }
}

@Composable
internal fun AssistantSubsectionTitle(
    title: String,
    subtitle: String = "",
) {
    SettingsSectionHeader(title = title, description = subtitle)
}

@Composable
internal fun AssistantSectionDivider() {
    val palette = rememberSettingsPalette()
    HorizontalDivider(color = palette.border)
}
