package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.SearchSettings
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.model.SearchSourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchPickerSheet(
    searchEnabled: Boolean,
    searchAvailable: Boolean,
    searchSettings: SearchSettings,
    currentModelIsImageGeneration: Boolean,
    currentModelSupportsTools: Boolean,
    selectedSearchSource: SearchSourceConfig?,
    selectedSearchProvider: ProviderSettings?,
    currentModel: String,
    onDismissRequest: () -> Unit,
    onToggleSearch: () -> Unit,
    onSelectSearchSource: (String) -> Unit,
    onUpdateSearchResultCount: (Int) -> Unit,
    onOpenSearchSettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchStatusMessage = when {
        currentModel.isBlank() -> "请先选择聊天模型"
        currentModelIsImageGeneration -> "当前是生图模型，搜索工具不可用"
        !currentModelSupportsTools -> "当前模型不支持工具调用，无法使用联网搜索"
        !searchAvailable -> "当前搜索源尚未配置完成，请先完成配置"
        else -> "搜索可用，会在模型调用 search_web 时接入当前默认搜索源"
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 8.dp,
                end = 20.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "网络搜索",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "在聊天里快速查看搜索状态、切换默认搜索源和调整搜索返回条数。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "聊天内搜索",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (searchEnabled) "已开启" else "已关闭",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = searchEnabled,
                            onCheckedChange = { onToggleSearch() },
                            enabled = searchAvailable,
                        )
                    }
                }
            }

            item {
                SearchStatusCard(
                    message = searchStatusMessage,
                    selectedSearchSource = selectedSearchSource,
                    selectedSearchProvider = selectedSearchProvider,
                )
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "默认返回条数",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${searchSettings.defaultResultCount} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = searchSettings.defaultResultCount.toFloat(),
                            onValueChange = { onUpdateSearchResultCount(it.toInt()) },
                            valueRange = 1f..10f,
                            steps = 8,
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "默认搜索源",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        searchSettings.sources.forEach { source ->
                            SearchSourceRow(
                                source = source,
                                selected = searchSettings.selectedSourceId == source.id,
                                selectedProvider = if (source.type == SearchSourceType.LLM_SEARCH) selectedSearchProvider else null,
                                onSelect = { onSelectSearchSource(source.id) },
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onOpenSearchSettings) {
                        Text("前往搜索与工具设置")
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchStatusCard(
    message: String,
    selectedSearchSource: SearchSourceConfig?,
    selectedSearchProvider: ProviderSettings?,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "当前状态",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            selectedSearchSource?.let { source ->
                Text(
                    text = buildString {
                        append("搜索源：")
                        append(source.name)
                        if (source.type == SearchSourceType.LLM_SEARCH) {
                            append(" · 代理模式")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            selectedSearchProvider?.let { provider ->
                Text(
                    text = buildString {
                        append("代理提供商：")
                        append(provider.name)
                        append(" · ")
                        append(
                            when (provider.resolvedApiProtocol()) {
                                ProviderApiProtocol.OPENAI_COMPATIBLE -> {
                                    if (provider.resolvedOpenAiTextApiMode() == com.example.myapplication.model.OpenAiTextApiMode.RESPONSES) {
                                        "OpenAI 兼容 · Responses"
                                    } else {
                                        "OpenAI 兼容 · Chat Completions"
                                    }
                                }

                                ProviderApiProtocol.ANTHROPIC -> "Anthropic /messages"
                            },
                        )
                        append(" · 搜索模型：")
                        append(
                            when (provider.resolveFunctionModelMode(ProviderFunction.SEARCH)) {
                                ProviderFunctionModelMode.FOLLOW_DEFAULT -> "跟随默认"
                                ProviderFunctionModelMode.CUSTOM -> provider.resolveFunctionModel(ProviderFunction.SEARCH).ifBlank { "未设置" }
                                ProviderFunctionModelMode.DISABLED -> "已关闭"
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchSourceRow(
    source: SearchSourceConfig,
    selected: Boolean,
    selectedProvider: ProviderSettings?,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = when (source.type) {
                    SearchSourceType.BRAVE -> "Brave 网页搜索"
                    SearchSourceType.TAVILY -> "Tavily 网页搜索"
                    SearchSourceType.GOOGLE_CSE -> "Google 自定义搜索"
                    SearchSourceType.LLM_SEARCH -> buildString {
                        append("把 search_web 代理给独立搜索模型")
                        if (selectedProvider != null) {
                            append(" · 当前：")
                            append(selectedProvider.name)
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
