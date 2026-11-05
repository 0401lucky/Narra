package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi

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

    val outlineColors = providerTextFieldColors()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = NarraUi.space16, top = NarraUi.space8,
            end = NarraUi.space16, bottom = NarraUi.space24
        ),
        verticalArrangement = Arrangement.spacedBy(NarraUi.space16),
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
                shape = NarraShapes.medium,
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
                shape = NarraShapes.medium,
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
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
                ) {
                    ProviderSectionHeader(
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
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
                ) {
                    ProviderSectionHeader(
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
                shape = NarraShapes.medium,
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
                    shape = NarraShapes.medium,
                    colors = outlineColors,
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = NarraUi.space8),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.space16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = NarraShapes.pill,
                    color = palette.surfaceTint,
                    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(NarraMinimumTouchTarget)
                        .clip(NarraShapes.pill)
                        .semantics {
                            if (providerCount <= 1) stateDescription = "至少保留一个提供商"
                        }
                        .clickable(enabled = providerCount > 1, role = Role.Button) {
                            onDeleteProvider(provider.id)
                            onNavigateBack()
                        },
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除提供商",
                        modifier = Modifier.padding(NarraUi.space12),
                        tint = if (providerCount > 1) palette.title else palette.body,
                    )
                }

                NarraButton(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = NarraMinimumTouchTarget)
                        .semantics { if (isSaving) stateDescription = "正在保存设置" },
                    shape = NarraShapes.medium,
                    contentPadding = PaddingValues(
                        horizontal = NarraUi.space12,
                        vertical = NarraUi.space12
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentStrong,
                        contentColor = palette.accentOnStrong,
                        disabledContainerColor = palette.surfaceTint,
                        disabledContentColor = palette.body,
                    ),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Save,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
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
internal fun providerTextFieldColors(containerColor: Color = MaterialTheme.colorScheme.surface) =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedContainerColor = containerColor,
        focusedContainerColor = containerColor,
        cursorColor = MaterialTheme.colorScheme.onSurface,
    )

@Composable
private fun <T> ProviderProtocolOptions(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || fontScale > 1.15f) {
            Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = NarraMinimumTouchTarget)
                            .clip(NarraShapes.small)
                            .background(if (option == selected) colors.primaryContainer else colors.surface)
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(horizontal = NarraUi.space8, vertical = NarraUi.space8),
                        horizontalArrangement = Arrangement.spacedBy(NarraUi.space12),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            text = label(option),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (option == selected) colors.onPrimaryContainer else colors.onSurface,
                        )
                    }
                }
            }
        } else {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            options.size,
                            baseShape = NarraShapes.medium
                        ),
                        modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
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
            .heightIn(min = NarraMinimumTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = NarraUi.space12),
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
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.accentOnStrong,
                checkedTrackColor = palette.accentStrong,
                uncheckedThumbColor = palette.body,
                uncheckedBorderColor = Color.Transparent,
                uncheckedTrackColor = palette.surfaceTint,
            ),
        )
    }
}

@Composable
private fun ProviderSectionHeader(title: String, description: String) {
    var showDescription by rememberSaveable(title, description) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NarraIconButton(onClick = { showDescription = true }) {
            Icon(
                Icons.Outlined.Info, contentDescription = "$title：查看说明",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
            )
        }
    }
    if (showDescription) {
        NarraAlertDialog(
            title = title,
            message = description,
            onDismiss = { showDescription = false },
            onConfirm = { showDescription = false },
            confirmLabel = "知道了",
            dismissLabel = null,
            shape = NarraShapes.large,
            actionContentColor = MaterialTheme.colorScheme.onSurface,
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
