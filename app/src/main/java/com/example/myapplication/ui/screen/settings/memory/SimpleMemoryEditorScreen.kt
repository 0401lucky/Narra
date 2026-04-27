package com.example.myapplication.ui.screen.settings.memory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.ui.component.TopAppSnackbarHost
import com.example.myapplication.ui.screen.settings.SettingsPalette
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette
import com.example.myapplication.ui.screen.settings.rememberSettingsSnackbarHostState
import com.example.myapplication.viewmodel.SimpleMemoryEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleMemoryEditorScreen(
    viewModel: SimpleMemoryEditorViewModel,
    assistantName: String,
    onOpenAdvancedManagement: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = state.message,
        onConsumeMessage = viewModel::consumeMessage,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(
                title = "长记忆",
                subtitle = assistantName.ifBlank { null },
                onNavigateBack = onNavigateBack,
                actionLabel = "高级管理",
                onAction = onOpenAdvancedManagement,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = SettingsScreenPadding,
                        end = SettingsScreenPadding,
                        bottom = 16.dp,
                    )
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionLabel(text = "核心记忆", palette = palette)

                MemoryDraftField(
                    value = state.coreDraft,
                    onValueChange = viewModel::updateCoreDraft,
                    placeholder = "在此编辑核心记忆，每行一条记忆点\n例如：\n- 用户喜欢简洁文风\n- 用户偏好夜间深色界面",
                    palette = palette,
                )

                HintRow(
                    text = "核心记忆跟随当前角色（或全局），保存时会与仓库比对：新增写入、删除移除；同内容条目保留原 id 与置顶状态。",
                    palette = palette,
                )

                if (state.coreOriginalEntries.isEmpty() && state.initialized) {
                    HintRow(
                        text = "当前角色暂无核心记忆，添加后保存即可创建。",
                        palette = palette,
                    )
                }

                if (state.showSceneSection) {
                    SectionLabel(text = "当前剧情记忆", palette = palette)

                    MemoryDraftField(
                        value = state.sceneDraft,
                        onValueChange = viewModel::updateSceneDraft,
                        placeholder = "AI 自动总结的剧情/情景/心理记忆会出现在这里\n例如：\n- 雨夜停在码头\n- 角色对调查表示警惕",
                        palette = palette,
                        minHeight = 240.dp,
                    )

                    HintRow(
                        text = "剧情记忆只属于当前会话，沉浸式自动总结的情景与心理状态都会写在这里；可手动编辑或清空。",
                        palette = palette,
                    )

                    if (state.sceneOriginalEntries.isEmpty() && state.initialized) {
                        HintRow(
                            text = "当前会话还没有剧情记忆，继续对话或手动添加后保存即可。",
                            palette = palette,
                        )
                    }
                }

                AdvancedSwitchRow(
                    palette = palette,
                    onClick = onOpenAdvancedManagement,
                )

                GradientSaveButton(
                    label = if (state.isBusy) "保存中…" else "保存记忆",
                    enabled = !state.isBusy,
                    isBusy = state.isBusy,
                    onClick = viewModel::save,
                )
            }
        }

        TopAppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentTopInset = 0.dp,
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    palette: SettingsPalette,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = palette.title,
    )
}

@Composable
private fun MemoryDraftField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    palette: SettingsPalette,
    minHeight: androidx.compose.ui.unit.Dp = 320.dp,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = palette.elevatedSurface,
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.36f)),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight),
            placeholder = {
                Text(
                    text = placeholder,
                    color = palette.body.copy(alpha = 0.5f),
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = palette.title,
                lineHeight = 24.sp,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = palette.accent,
            ),
        )
    }
}

@Composable
private fun HintRow(
    text: String,
    palette: SettingsPalette,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = palette.body.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.body,
        )
    }
}

@Composable
private fun AdvancedSwitchRow(
    palette: SettingsPalette,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = palette.surface,
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.32f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = null,
                tint = palette.accent,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "切换高级管理",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.title,
                )
                Text(
                    text = "查看分类记忆、剧情摘要与时间线，按角色筛选并 pin/解除置顶。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.body,
                )
            }
        }
    }
}

@Composable
private fun GradientSaveButton(
    label: String,
    enabled: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF7A5CFF),
            Color(0xFFE45CA0),
            Color(0xFFFF8A4C),
        ),
    )
    val disabledOverlay = Color.White.copy(alpha = if (enabled) 0f else 0.45f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = gradient, shape = RoundedCornerShape(28.dp))
                .background(color = disabledOverlay, shape = RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (isBusy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Text(
                        text = label,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Text(
                    text = label,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
