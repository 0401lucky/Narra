package com.example.myapplication.ui.screen.settings.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsSectionHeader
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette
import com.example.myapplication.ui.screen.settings.rememberSettingsSnackbarHostState
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.SimpleMemoryEditorUiState
import com.example.myapplication.viewmodel.SimpleMemoryEditorViewModel

@Composable
fun SimpleMemoryEditorScreen(
    viewModel: SimpleMemoryEditorViewModel,
    assistantName: String,
    onOpenAdvancedManagement: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SimpleMemoryEditorContent(
        state = state,
        assistantName = assistantName,
        onCoreDraftChange = viewModel::updateCoreDraft,
        onSceneDraftChange = viewModel::updateSceneDraft,
        onSave = viewModel::save,
        onConsumeMessage = viewModel::consumeMessage,
        onOpenAdvancedManagement = onOpenAdvancedManagement,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
internal fun SimpleMemoryEditorContent(
    state: SimpleMemoryEditorUiState,
    assistantName: String,
    onCoreDraftChange: (String) -> Unit,
    onSceneDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onConsumeMessage: () -> Unit,
    onOpenAdvancedManagement: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(state.message, onConsumeMessage)
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = palette.background,
        topBar = {
            SettingsTopBar(
                title = "长记忆",
                subtitle = assistantName.ifBlank { null },
                onNavigateBack = onNavigateBack,
                actionLabel = "高级管理",
                onAction = onOpenAdvancedManagement,
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = palette.surface) {
                Column(
                    modifier = Modifier.navigationBarsPadding()
                        .padding(horizontal = SettingsScreenPadding, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "保存后，删去的记忆也会从档案中移除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body,
                    )
                    Button(
                        onClick = onSave,
                        enabled = state.initialized && !state.isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("simple_memory_save"),
                        shape = RoundedCornerShape(NarraUi.smallRadius),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (state.isBusy) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                            Text(if (state.isBusy) "保存中…" else "保存记忆")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsScreenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(NarraUi.rowGap),
        ) {
            SettingsSectionHeader(
                title = "核心记忆",
                description = "每行一条记忆。作用范围跟随当前角色的记忆设置，可在高级管理中查看全局与角色条目。保存时会新增新内容、移除删去的内容；未改动的记忆保留原来的置顶状态。",
            )
            Text(
                text = when {
                    state.coreOriginalEntries.any { it.scopeType == MemoryScopeType.GLOBAL } -> "包含全局共享记忆"
                    else -> "跟随当前角色的记忆范围"
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.body,
            )
            OutlinedTextField(
                value = state.coreDraft,
                onValueChange = onCoreDraftChange,
                modifier = Modifier.fillMaxWidth().testTag("simple_memory_core"),
                enabled = state.initialized && !state.isBusy,
                label = { Text("核心记忆内容") },
                placeholder = { Text("每行一条记忆") },
                minLines = 7,
                maxLines = 14,
                colors = rememberSettingsOutlineColors(),
                shape = RoundedCornerShape(NarraUi.smallRadius),
            )
            if (state.coreOriginalEntries.isEmpty() && state.initialized) {
                Text("暂无核心记忆", style = MaterialTheme.typography.bodySmall, color = palette.body)
            }
            if (state.showSceneSection) {
                SettingsSectionHeader(
                    title = "当前剧情记忆",
                    description = "自动总结的情景与心理状态会记录在这里，也可以手动编辑。清空后保存会移除当前会话的剧情记忆。",
                )
                Text("仅限当前会话", style = MaterialTheme.typography.bodySmall, color = palette.body)
                OutlinedTextField(
                    value = state.sceneDraft,
                    onValueChange = onSceneDraftChange,
                    modifier = Modifier.fillMaxWidth().testTag("simple_memory_scene"),
                    enabled = state.initialized && !state.isBusy,
                    label = { Text("剧情记忆内容") },
                    placeholder = { Text("每行一条剧情记忆") },
                    minLines = 5,
                    maxLines = 12,
                    colors = rememberSettingsOutlineColors(),
                    shape = RoundedCornerShape(NarraUi.smallRadius),
                )
                if (state.sceneOriginalEntries.isEmpty() && state.initialized) {
                    Text("暂无剧情记忆", style = MaterialTheme.typography.bodySmall, color = palette.body)
                }
            }
        }
    }
}
