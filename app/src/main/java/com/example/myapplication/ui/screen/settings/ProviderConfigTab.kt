package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraIconButton

@Composable
internal fun ConfigTabContent(
    provider: ProviderSettings,
    isSaving: Boolean,
    providerCount: Int,
    onUpdateProviderName: (String, String) -> Unit,
    onUpdateProviderBaseUrl: (String, String) -> Unit,
    onUpdateProviderApiKey: (String, String) -> Unit,
    onUpdateProviderApiProtocol: (String, ProviderApiProtocol) -> Unit,
    onUpdateProviderOpenAiTextApiMode: (String, com.example.myapplication.model.OpenAiTextApiMode) -> Unit,
    onUpdateProviderChatCompletionsPath: (String, String) -> Unit,
    onToggleProviderEnabled: (String) -> Unit,
    onSave: () -> Unit,
    onDeleteProvider: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var showApiKey by rememberSaveable(provider.id) { mutableStateOf(false) }
    val palette = rememberSettingsPalette()
    val supportsProtocolSelection = provider.supportsAnthropicProtocolSelection()
    val resolvedApiProtocol = provider.resolvedApiProtocol()
    val supportsOpenAiTextApiModeSelection = provider.supportsOpenAiTextApiModeSelection()
    val resolvedOpenAiTextApiMode = provider.resolvedOpenAiTextApiMode()
    val baseUrlPlaceholder = when (resolvedApiProtocol) {
        ProviderApiProtocol.OPENAI_COMPATIBLE -> "https://api.openai.com/v1/"
        ProviderApiProtocol.ANTHROPIC -> "https://api.anthropic.com/v1/"
    }

    val outlineColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.accentStrong,
        unfocusedBorderColor = palette.border.copy(alpha = 0.8f),
        focusedLabelColor = palette.accentStrong,
        unfocusedLabelColor = palette.body,
        unfocusedContainerColor = palette.surface,
        focusedContainerColor = palette.surface,
        cursorColor = palette.accentStrong,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            SettingSwitchRow(
                title = "是否启用",
                checked = provider.enabled,
                onCheckedChange = { onToggleProviderEnabled(provider.id) },
            )
        }

        item {
            OutlinedTextField(
                value = provider.name,
                onValueChange = { onUpdateProviderName(provider.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称", fontWeight = FontWeight.Medium) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = outlineColors,
            )
        }

        item {
            OutlinedTextField(
                value = provider.apiKey,
                onValueChange = { onUpdateProviderApiKey(provider.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key", fontWeight = FontWeight.Medium) },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = outlineColors,
                trailingIcon = {
                    NarraIconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showApiKey) "隐藏密钥" else "显示密钥",
                            tint = palette.accentStrong,
                        )
                    }
                },
            )
        }

        if (supportsProtocolSelection) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingsSectionHeader(
                        title = "接口协议",
                        description = providerProtocolDescription(resolvedApiProtocol),
                    )
                    ProviderProtocolOptions(
                        options = ProviderApiProtocol.entries,
                        selected = resolvedApiProtocol,
                        label = { it.label },
                        onSelect = { onUpdateProviderApiProtocol(provider.id, it) },
                    )
                }
            }
        }

        if (supportsOpenAiTextApiModeSelection) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingsSectionHeader(
                        title = "文本接口模式",
                        description = when (resolvedOpenAiTextApiMode) {
                            com.example.myapplication.model.OpenAiTextApiMode.CHAT_COMPLETIONS -> "使用 chat/completions，适合大多数 OpenAI 兼容接口。第三方端点可在下方自定义请求路径。"
                            com.example.myapplication.model.OpenAiTextApiMode.RESPONSES -> "使用 OpenAI /responses，适合支持该协议的服务。部分功能的服务兼容性可能不同。"
                        },
                    )
                    ProviderProtocolOptions(
                        options = com.example.myapplication.model.OpenAiTextApiMode.entries,
                        selected = resolvedOpenAiTextApiMode,
                        label = { it.label },
                        onSelect = { onUpdateProviderOpenAiTextApiMode(provider.id, it) },
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = provider.baseUrl,
                onValueChange = { onUpdateProviderBaseUrl(provider.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("服务地址", fontWeight = FontWeight.Medium) },
                placeholder = { Text(baseUrlPlaceholder) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = outlineColors,
            )
        }

        if (supportsOpenAiTextApiModeSelection &&
            resolvedApiProtocol == ProviderApiProtocol.OPENAI_COMPATIBLE &&
            resolvedOpenAiTextApiMode == com.example.myapplication.model.OpenAiTextApiMode.CHAT_COMPLETIONS
        ) {
            item {
                OutlinedTextField(
                    value = provider.resolvedChatCompletionsPath(),
                    onValueChange = { onUpdateProviderChatCompletionsPath(provider.id, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("请求路径", fontWeight = FontWeight.Medium) },
                    placeholder = { Text(com.example.myapplication.model.DEFAULT_CHAT_COMPLETIONS_PATH) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = outlineColors,
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = palette.surfaceTint,
                    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable(enabled = providerCount > 1) {
                            onDeleteProvider(provider.id)
                            onNavigateBack()
                        },
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除提供商",
                        modifier = Modifier.padding(16.dp),
                        tint = if (providerCount > 1) palette.title else palette.body.copy(alpha = 0.3f),
                    )
                }

                NarraButton(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentStrong,
                        contentColor = palette.accentOnStrong,
                    ),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            if (isSaving) "保存中..." else "保存设置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ProviderProtocolOptions(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || fontScale > 1.15f) {
            Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            text = label(option),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        } else {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        label = { Text(label(option)) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = rememberSettingsPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = palette.title,
            fontWeight = FontWeight.Bold,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.surface,
                checkedTrackColor = palette.accentStrong,
                uncheckedThumbColor = palette.body.copy(alpha = 0.6f),
                uncheckedBorderColor = Color.Transparent,
                uncheckedTrackColor = palette.surfaceTint,
            ),
        )
    }
}

private fun providerProtocolDescription(apiProtocol: ProviderApiProtocol): String {
    return when (apiProtocol) {
        ProviderApiProtocol.ANTHROPIC -> {
            "Anthropic 模式会走 /v1/messages，并自动附带 x-api-key 与 anthropic-version。"
        }
        ProviderApiProtocol.OPENAI_COMPATIBLE -> {
            "OpenAI 兼容协议适用于大多数兼容网关与中转站。具体请求接口由文本接口模式和请求路径决定。"
        }
    }
}
