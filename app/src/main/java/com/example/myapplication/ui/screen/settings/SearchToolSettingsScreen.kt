package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.model.SearchSourceType
import com.example.myapplication.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchToolSettingsScreen(
    uiState: SettingsUiState,
    onSelectSource: (String) -> Unit,
    onUpdateResultCount: (Int) -> Unit,
    onUpdateSourceEnabled: (String, Boolean) -> Unit,
    onUpdateSourceApiKey: (String, String) -> Unit,
    onUpdateSourceEngineId: (String, String) -> Unit,
    onUpdateSourceProviderId: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    BackHandler(onBack = onNavigateBack)

    val effectiveSettings = uiState.savedSettings.copy(
        providers = uiState.providers,
        selectedProviderId = uiState.selectedProviderId,
    )
    val selectedLlmSource = uiState.searchSettings.sources.firstOrNull { source ->
        source.type == SearchSourceType.LLM_SEARCH
    }
    val selectedLlmProvider = selectedLlmSource?.let(effectiveSettings::resolveSearchSourceProvider)

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "搜索与工具",
                onNavigateBack = onNavigateBack,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = SettingsScreenPadding,
                    top = 4.dp,
                    end = SettingsScreenPadding,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    SettingsSectionHeader("默认行为", "")
                }
                item {
                    SettingsGroup {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "LLM 搜索的工作方式",
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "主聊天模型只会调用统一的 search_web 工具。若这里选中 LLM 搜索，工具执行器会把请求转发给你在下方单独选择的搜索提供商与搜索模型；最终回答仍由主聊天模型生成。",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = buildString {
                                    append("当前 LLM 搜索提供商：")
                                    append(selectedLlmProvider?.name.orEmpty().ifBlank { "未选择" })
                                    append(" · 搜索模型：")
                                    append(selectedLlmProvider?.searchModel.orEmpty().ifBlank { "未设置" })
                                },
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    SettingsGroup {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "默认返回条数",
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "${uiState.searchSettings.defaultResultCount} 条",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Slider(
                                value = uiState.searchSettings.defaultResultCount.toFloat(),
                                onValueChange = { onUpdateResultCount(it.toInt()) },
                                valueRange = 1f..10f,
                                steps = 8,
                            )
                            Text(
                                text = "聊天页开启搜索后，模型每次默认最多读取这些搜索结果。",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    SettingsSectionHeader("搜索源", "")
                }
                items(uiState.searchSettings.sources.size) { index ->
                    val source = uiState.searchSettings.sources[index]
                    SearchSourceCard(
                        settings = effectiveSettings,
                        source = source,
                        selected = uiState.searchSettings.selectedSourceId == source.id,
                        onSelect = { onSelectSource(source.id) },
                        onEnabledChange = { onUpdateSourceEnabled(source.id, it) },
                        onApiKeyChange = { onUpdateSourceApiKey(source.id, it) },
                        onEngineIdChange = { onUpdateSourceEngineId(source.id, it) },
                        onProviderSelected = { onUpdateSourceProviderId(source.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSourceCard(
    settings: com.example.myapplication.model.AppSettings,
    source: SearchSourceConfig,
    selected: Boolean,
    onSelect: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onEngineIdChange: (String) -> Unit,
    onProviderSelected: (String) -> Unit,
) {
    val providerOptions = settings.enabledProviders()
    val selectedProvider = settings.resolveSearchSourceProvider(source)
    val statusText = when (source.type) {
        SearchSourceType.LLM_SEARCH -> {
            when {
                !source.enabled -> "启用后，主聊天模型会把 search_web 调用转发给这里配置的搜索提供商。"
                providerOptions.isEmpty() -> "当前没有可用提供商，请先在提供商页完成配置。"
                selectedProvider == null -> "请先为 LLM 搜索选择一个独立的搜索提供商。"
                !selectedProvider.supportsLlmSearchSource() -> {
                    "所选搜索提供商需要使用 Responses API 或 Anthropic 协议。"
                }
                selectedProvider.searchModel.isBlank() -> {
                    "已启用，但该搜索提供商还没单独设置搜索模型；当前会回退使用它自己的聊天模型搜索。"
                }
                else -> "配置完整，聊天页可用。"
            }
        }

        else -> {
            if (source.isConfigured()) {
                "配置完整，聊天页可用。"
            } else {
                "需要启用并填写完整凭据后，聊天页才会允许模型调用该搜索源。"
            }
        }
    }

    SettingsGroup {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = source.name,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = when (source.type) {
                            SearchSourceType.BRAVE -> "使用 Brave Search API"
                            SearchSourceType.TAVILY -> "使用 Tavily Search API"
                            SearchSourceType.GOOGLE_CSE -> "使用 Google Programmable Search"
                            SearchSourceType.LLM_SEARCH -> "主聊天模型调用搜索工具后，将转发给这里配置的搜索提供商与搜索模型"
                        },
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = source.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onSelect,
                )
                Text(
                    text = if (selected) "当前默认搜索源" else "设为默认搜索源",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }

            if (source.type != SearchSourceType.LLM_SEARCH) {
                OutlinedTextField(
                    value = source.apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    singleLine = true,
                )
            }

            if (source.type == SearchSourceType.LLM_SEARCH) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "搜索提供商",
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    )
                    providerOptions.forEach { provider ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(
                                selected = source.providerId == provider.id,
                                onClick = { onProviderSelected(provider.id) },
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = provider.name,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = buildString {
                                        append(
                                            when (provider.resolvedApiProtocol()) {
                                                com.example.myapplication.model.ProviderApiProtocol.OPENAI_COMPATIBLE -> {
                                                    if (provider.resolvedOpenAiTextApiMode() == com.example.myapplication.model.OpenAiTextApiMode.RESPONSES) {
                                                        "OpenAI 兼容 · Responses"
                                                    } else {
                                                        "OpenAI 兼容 · Chat Completions"
                                                    }
                                                }

                                                com.example.myapplication.model.ProviderApiProtocol.ANTHROPIC -> "Anthropic /messages"
                                            },
                                        )
                                        append(" · 搜索模型：")
                                        append(provider.resolveFunctionModel(com.example.myapplication.model.ProviderFunction.SEARCH).ifBlank { "未设置" })
                                        if (provider.id == settings.selectedProviderId) {
                                            append(" · 当前聊天提供商")
                                        }
                                    },
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (source.type == SearchSourceType.GOOGLE_CSE) {
                OutlinedTextField(
                    value = source.engineId,
                    onValueChange = onEngineIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索引擎 ID（cx）") },
                    singleLine = true,
                )
            }

            Text(
                text = statusText,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
