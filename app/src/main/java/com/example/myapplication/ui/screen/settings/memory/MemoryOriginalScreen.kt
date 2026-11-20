package com.example.myapplication.ui.screen.settings.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.model.MessageRole
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.MemoryOriginalUiState

@Composable
fun MemoryOriginalScreen(
    state: MemoryOriginalUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(topBar = {
        SettingsTopBar(
            title = "只读历史原文",
            subtitle = state.title,
            onNavigateBack = onNavigateBack,
            titleMaxLines = 2
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("memory_original_list"),
            contentPadding = PaddingValues(NarraUi.space16),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space16)
        ) {
            item {
                Text(
                    "原始消息与当时署名。查看原文不会重新进入剧情或改动当前会话。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.missing) item {
                Text(
                    "原文缺失或不属于此档案，无法显示。",
                    modifier = Modifier.testTag("memory_original_missing")
                )
            }
            items(state.messages, key = { it.message.id }) { row ->
                ArchiveCard {
                    Text(
                        row.message.speakerName.ifBlank { if (row.message.role == MessageRole.USER) "用户 · 历史署名未知" else "角色 · 历史署名未知" },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "${archiveDate(row.message.createdAt)} · 身份 ${row.personaKey}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    SelectionContainer { Text(if (row.message.isRecalled) "此消息已撤回" else row.message.content.ifBlank { "此消息没有文字正文" }) }
                }
            }
            item {
                if (state.isLoading) Text("正在读取原文…")
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error); ArchiveAction(
                    "重试读取",
                    onRetry
                )
                }
                if (state.hasMore) ArchiveAction("加载更多原文", onLoadMore, !state.isLoading)
                if (!state.isLoading && !state.missing && state.error == null) Text("已显示 ${state.messages.size} / ${state.totalCount} 条消息")
            }
        }
    }
}
