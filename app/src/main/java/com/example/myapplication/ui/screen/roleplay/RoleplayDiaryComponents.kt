package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi

@Composable
internal fun RoleplayDiaryPage(
    backgroundUri: String,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ApplyRoleplaySurfaceSystemBars(scheme.background, RoleplayImmersiveMode.NONE)
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        // 用户背景仍保留在底层，正文与顶栏使用不透明表面保证阅读对比度。
        if (backgroundUri.isNotBlank()) {
            RoleplaySceneBackground(
                backgroundUri = backgroundUri,
                fallbackBackgroundColor = scheme.background,
                modifier = Modifier.fillMaxSize().roleplayImmersivePadding(RoleplayImmersiveMode.NONE),
            )
        }
        content()
    }
}

@Composable
internal fun RoleplayDiaryTopBar(
    title: String,
    subtitle: String,
    onNavigateBack: () -> Unit,
    onOpenMailbox: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiaryIconButton(Lucide.ArrowLeft, "返回", onNavigateBack)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        lineHeight = 24.sp,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None,
                        ),
                    ),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("diary_top_title"),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DiaryIconButton(Lucide.Mail, "信箱", onOpenMailbox)
            actions()
        }
    }
}

@Composable
internal fun DiaryIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    NarraIconButton(onClick = onClick, modifier = Modifier.size(NarraMinimumTouchTarget)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoleplayDiaryMetadata(
    entry: RoleplayDiaryEntry,
    mutedColor: Color,
    tagLimit: Int,
) {
    if (entry.mood.isBlank() && entry.weather.isBlank() && entry.tags.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NarraUi.rowGap),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (entry.mood.isNotBlank()) {
            Text(entry.mood, style = MaterialTheme.typography.labelMedium, color = mutedColor)
        }
        if (entry.weather.isNotBlank()) {
            Text(entry.weather, style = MaterialTheme.typography.labelMedium, color = mutedColor)
        }
        entry.tags.take(tagLimit).forEach { tag ->
            Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
