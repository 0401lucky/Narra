package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.roleplay.script.RoleplayScriptDefinition
import com.example.myapplication.roleplay.script.RoleplayScriptEvent
import com.example.myapplication.roleplay.script.RoleplayScriptPermission
import com.example.myapplication.roleplay.script.RoleplayScriptScope
import com.example.myapplication.ui.component.NarraFilledTonalButton
import com.example.myapplication.ui.component.NarraOutlinedButton
import com.example.myapplication.ui.component.TopAppSnackbarHost
import com.example.myapplication.viewmodel.RoleplayScriptDraft
import com.example.myapplication.viewmodel.RoleplayScriptLabUiState
import com.example.myapplication.viewmodel.RoleplayScriptTemplate
import com.example.myapplication.viewmodel.RoleplayScriptTestResult
import com.example.myapplication.viewmodel.RoleplayScriptTestState

data class RoleplayScriptBindingOption(
    val scope: RoleplayScriptScope,
    val id: String,
    val title: String,
    val subtitle: String,
)

@Composable
fun RoleplayScriptLabScreen(
    uiState: RoleplayScriptLabUiState,
    bindingOptions: List<RoleplayScriptBindingOption>,
    onCreateScript: () -> Unit,
    onSelectScript: (String) -> Unit,
    onApplyTemplate: (String) -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateScope: (RoleplayScriptScope) -> Unit,
    onUpdateOwnerId: (String) -> Unit,
    onUpdateSource: (String) -> Unit,
    onUpdateEnabled: (Boolean) -> Unit,
    onTogglePermission: (RoleplayScriptPermission) -> Unit,
    onUpdateTestEvent: (RoleplayScriptEvent) -> Unit,
    onUpdateTestUserText: (String) -> Unit,
    onUpdateTestPromptText: (String) -> Unit,
    onUpdateTestAssistantText: (String) -> Unit,
    onUpdateTestVariablesText: (String) -> Unit,
    onRunScriptTest: () -> Unit,
    onSaveScript: () -> Unit,
    onDeleteSelectedScript: () -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "脚本实验室",
                subtitle = "Roleplay JS",
                onNavigateBack = onNavigateBack,
                actionLabel = "保存",
                onAction = onSaveScript,
                actionEnabled = !uiState.isSaving,
            )
        },
        containerColor = palette.background,
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
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
                    bottom = 36.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    SettingsPageIntro(
                        title = "多层 RP 脚本",
                        summary = "可以先从模板开始，再用试运行确认它会改什么。",
                    )
                }
                item {
                    SettingsSectionHeader("快速模板", "")
                }
                item {
                    RoleplayScriptTemplateList(
                        templates = uiState.templates,
                        onApplyTemplate = onApplyTemplate,
                    )
                }
                item {
                    NarraFilledTonalButton(
                        onClick = onCreateScript,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("新建脚本")
                    }
                }
                item {
                    SettingsSectionHeader("脚本列表", "")
                }
                item {
                    SettingsGroup {
                        if (uiState.scripts.isEmpty()) {
                            SettingsPlaceholderRow(
                                title = "暂无脚本",
                                subtitle = "可以先新建一个会话脚本。",
                            )
                        } else {
                            uiState.scripts.forEachIndexed { index, script ->
                                RoleplayScriptListRow(
                                    script = script,
                                    selected = script.id == uiState.selectedScriptId,
                                    onClick = { onSelectScript(script.id) },
                                )
                                if (index != uiState.scripts.lastIndex) {
                                    SettingsGroupDivider()
                                }
                            }
                        }
                    }
                }
                item {
                    SettingsSectionHeader("编辑", "")
                }
                item {
                    RoleplayScriptEditor(
                        draft = uiState.draft,
                        hasSelection = uiState.hasSelection,
                        isSaving = uiState.isSaving,
                        onUpdateName = onUpdateName,
                        onUpdateScope = onUpdateScope,
                        onUpdateOwnerId = onUpdateOwnerId,
                        onUpdateSource = onUpdateSource,
                        onUpdateEnabled = onUpdateEnabled,
                        bindingOptions = bindingOptions,
                        onTogglePermission = onTogglePermission,
                        onSaveScript = onSaveScript,
                        onDeleteSelectedScript = onDeleteSelectedScript,
                    )
                }
                item {
                    SettingsSectionHeader("试运行", "")
                }
                item {
                    RoleplayScriptTestPanel(
                        test = uiState.test,
                        onUpdateEvent = onUpdateTestEvent,
                        onUpdateUserText = onUpdateTestUserText,
                        onUpdatePromptText = onUpdateTestPromptText,
                        onUpdateAssistantText = onUpdateTestAssistantText,
                        onUpdateVariablesText = onUpdateTestVariablesText,
                        onRunScriptTest = onRunScriptTest,
                    )
                }
            }
            TopAppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentTopInset = innerPadding.calculateTopPadding(),
            )
        }
    }
}

@Composable
private fun RoleplayScriptListRow(
    script: RoleplayScriptDefinition,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    SettingsListRow(
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                tint = palette.title,
            )
        },
        title = script.name.ifBlank { "未命名脚本" },
        supportingText = buildScriptSummary(script),
        highlighted = selected,
        onClick = onClick,
        trailingContent = {
            SettingsStatusPill(
                text = if (script.enabled) "启用" else "停用",
                containerColor = if (script.enabled) palette.accentSoft else palette.surfaceTint,
                contentColor = if (script.enabled) palette.accent else palette.body,
            )
        },
    )
}

@Composable
private fun RoleplayScriptTemplateList(
    templates: List<RoleplayScriptTemplate>,
    onApplyTemplate: (String) -> Unit,
) {
    SettingsGroup {
        templates.forEachIndexed { index, template ->
            SettingsListRow(
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = rememberSettingsPalette().title,
                    )
                },
                title = template.title,
                supportingText = "${template.recommendedScope.label()} · ${template.summary}",
                trailingContent = {
                    NarraOutlinedButton(
                        onClick = { onApplyTemplate(template.id) },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("套用")
                    }
                },
            )
            if (index != templates.lastIndex) {
                SettingsGroupDivider()
            }
        }
    }
}

@Composable
private fun RoleplayScriptEditor(
    draft: RoleplayScriptDraft,
    hasSelection: Boolean,
    isSaving: Boolean,
    onUpdateName: (String) -> Unit,
    onUpdateScope: (RoleplayScriptScope) -> Unit,
    onUpdateOwnerId: (String) -> Unit,
    onUpdateSource: (String) -> Unit,
    onUpdateEnabled: (Boolean) -> Unit,
    bindingOptions: List<RoleplayScriptBindingOption>,
    onTogglePermission: (RoleplayScriptPermission) -> Unit,
    onSaveScript: () -> Unit,
    onDeleteSelectedScript: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    SettingsGroup {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "启用脚本",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.title,
                    )
                    Text(
                        text = if (draft.enabled) "参与剧情回合" else "暂不执行",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body,
                    )
                }
                Switch(
                    checked = draft.enabled,
                    onCheckedChange = onUpdateEnabled,
                )
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = onUpdateName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                singleLine = true,
                colors = rememberSettingsOutlineColors(),
            )
            RoleplayScriptScopeSelector(
                selectedScope = draft.scope,
                onSelectScope = onUpdateScope,
            )
            if (draft.scope != RoleplayScriptScope.GLOBAL) {
                RoleplayScriptBindingSelector(
                    scope = draft.scope,
                    ownerId = draft.ownerId,
                    bindingOptions = bindingOptions,
                    onSelectOwner = onUpdateOwnerId,
                )
            }
            OutlinedTextField(
                value = draft.source,
                onValueChange = onUpdateSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                label = { Text("JavaScript") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                minLines = 10,
                colors = rememberSettingsOutlineColors(),
            )
        }
        SettingsGroupDivider()
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = palette.title)
                Text(
                    text = "权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.title,
                )
            }
            RoleplayScriptPermission.entries.forEach { permission ->
                RoleplayScriptPermissionRow(
                    permission = permission,
                    checked = permission in draft.grantedPermissions,
                    onToggle = { onTogglePermission(permission) },
                )
            }
        }
        SettingsGroupDivider()
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NarraFilledTonalButton(
                onClick = onSaveScript,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存")
            }
            NarraOutlinedButton(
                onClick = onDeleteSelectedScript,
                enabled = hasSelection && !isSaving,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("删除")
            }
        }
    }
}

@Composable
private fun RoleplayScriptBindingSelector(
    scope: RoleplayScriptScope,
    ownerId: String,
    bindingOptions: List<RoleplayScriptBindingOption>,
    onSelectOwner: (String) -> Unit,
) {
    val palette = rememberSettingsPalette()
    val options = bindingOptions.filter { it.scope == scope }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "绑定目标",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.title,
        )
        if (options.isEmpty()) {
            Text(
                text = "当前没有可选择的${scope.label()}，可以先手动填写 ID。",
                style = MaterialTheme.typography.bodySmall,
                color = palette.body,
            )
        } else {
            options.take(MAX_BINDING_OPTIONS_IN_EDITOR).forEach { option ->
                RoleplayScriptBindingOptionRow(
                    option = option,
                    selected = option.id == ownerId,
                    onClick = { onSelectOwner(option.id) },
                )
            }
        }
        OutlinedTextField(
            value = ownerId,
            onValueChange = onSelectOwner,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("${scope.label()} ID") },
            singleLine = true,
            colors = rememberSettingsOutlineColors(),
        )
    }
}

@Composable
private fun RoleplayScriptBindingOptionRow(
    option: RoleplayScriptBindingOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        color = if (selected) palette.accentSoft.copy(alpha = 0.68f) else palette.surfaceTint,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.42f)),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                SettingsStatusPill(
                    text = "已选",
                    containerColor = palette.accentSoft,
                    contentColor = palette.accent,
                )
            }
        }
    }
}

@Composable
private fun RoleplayScriptTestPanel(
    test: RoleplayScriptTestState,
    onUpdateEvent: (RoleplayScriptEvent) -> Unit,
    onUpdateUserText: (String) -> Unit,
    onUpdatePromptText: (String) -> Unit,
    onUpdateAssistantText: (String) -> Unit,
    onUpdateVariablesText: (String) -> Unit,
    onRunScriptTest: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    SettingsGroup {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "执行节点",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.title,
            )
            RoleplayScriptEventSelector(
                selectedEvent = test.event,
                onSelectEvent = onUpdateEvent,
            )
            OutlinedTextField(
                value = test.userText,
                onValueChange = onUpdateUserText,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("用户文本") },
                minLines = 2,
                colors = rememberSettingsOutlineColors(),
            )
            OutlinedTextField(
                value = test.promptText,
                onValueChange = onUpdatePromptText,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("当前提示词") },
                minLines = 2,
                colors = rememberSettingsOutlineColors(),
            )
            OutlinedTextField(
                value = test.assistantText,
                onValueChange = onUpdateAssistantText,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("角色回复") },
                minLines = 2,
                colors = rememberSettingsOutlineColors(),
            )
            OutlinedTextField(
                value = test.variablesText,
                onValueChange = onUpdateVariablesText,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("变量，每行 key=value") },
                minLines = 2,
                colors = rememberSettingsOutlineColors(),
            )
            NarraFilledTonalButton(
                onClick = onRunScriptTest,
                enabled = !test.isRunning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(if (test.isRunning) "运行中" else "运行当前脚本")
            }
        }
        test.result?.let { result ->
            SettingsGroupDivider()
            RoleplayScriptTestResultView(result)
        }
    }
}

@Composable
private fun RoleplayScriptEventSelector(
    selectedEvent: RoleplayScriptEvent,
    onSelectEvent: (RoleplayScriptEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoleplayScriptEvent.entries.forEach { event ->
            RoleplayScriptChoiceChip(
                text = event.label(),
                selected = event == selectedEvent,
                onClick = { onSelectEvent(event) },
            )
        }
    }
}

@Composable
private fun RoleplayScriptTestResultView(result: RoleplayScriptTestResult) {
    val palette = rememberSettingsPalette()
    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "运行结果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.title,
            )
            SettingsStatusPill(
                text = if (result.available) "可用" else "不可用",
                containerColor = if (result.available) palette.accentSoft else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (result.available) palette.accent else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (!result.available) {
            RoleplayScriptResultBlock("原因", result.unavailableReason.ifBlank { "当前设备无法运行脚本沙盒" })
        }
        RoleplayScriptResultBlock(
            title = "追加提示",
            body = result.promptAdditions.joinToString(separator = "\n").ifBlank { "无" },
        )
        RoleplayScriptResultBlock(
            title = "改写发送",
            body = result.outgoingMessage?.takeIf(String::isNotBlank) ?: "无",
        )
        RoleplayScriptResultBlock(
            title = "变量变化",
            body = result.variables.entries.joinToString(separator = "\n") { (key, value) -> "$key=$value" }
                .ifBlank { "无" },
        )
        RoleplayScriptResultBlock(
            title = "界面提示",
            body = result.uiDirectives.joinToString(separator = "\n") { directive ->
                "${directive.type}: ${directive.payload}"
            }.ifBlank { "无" },
        )
        RoleplayScriptResultBlock(
            title = "日志",
            body = result.logs.joinToString(separator = "\n").ifBlank { "无" },
        )
    }
}

@Composable
private fun RoleplayScriptResultBlock(
    title: String,
    body: String,
) {
    val palette = rememberSettingsPalette()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = palette.title,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = palette.body,
        )
    }
}

@Composable
private fun RoleplayScriptScopeSelector(
    selectedScope: RoleplayScriptScope,
    onSelectScope: (RoleplayScriptScope) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoleplayScriptScope.entries.forEach { scope ->
            RoleplayScriptChoiceChip(
                text = scope.label(),
                selected = scope == selectedScope,
                onClick = { onSelectScope(scope) },
            )
        }
    }
}

@Composable
private fun RoleplayScriptPermissionRow(
    permission: RoleplayScriptPermission,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = permission.label(),
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (permission.dangerous) {
                    SettingsStatusPill(
                        text = "危险",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Text(
                text = permission.storageValue,
                style = MaterialTheme.typography.bodySmall,
                color = palette.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
        )
    }
}

@Composable
private fun RoleplayScriptChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) palette.accentSoft else palette.surfaceTint,
        contentColor = if (selected) palette.accent else palette.body,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.42f)),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(Modifier),
    ) {
        TextButton(onClick = onClick) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

private fun buildScriptSummary(script: RoleplayScriptDefinition): String {
    val ownerLabel = when (script.scope) {
        RoleplayScriptScope.GLOBAL -> "全局"
        else -> script.ownerId.ifBlank { "未绑定" }
    }
    return "${script.scope.label()} · $ownerLabel · ${script.grantedPermissions.size} 项权限"
}

private fun RoleplayScriptScope.label(): String {
    return when (this) {
        RoleplayScriptScope.GLOBAL -> "全局"
        RoleplayScriptScope.CHARACTER -> "角色"
        RoleplayScriptScope.SCENARIO -> "场景"
        RoleplayScriptScope.SESSION -> "会话"
    }
}

private fun RoleplayScriptEvent.label(): String {
    return when (this) {
        RoleplayScriptEvent.ON_SESSION_START -> "进入会话"
        RoleplayScriptEvent.BEFORE_PROMPT -> "生成前"
        RoleplayScriptEvent.BEFORE_SEND -> "发送前"
        RoleplayScriptEvent.AFTER_ASSISTANT -> "回复后"
        RoleplayScriptEvent.RENDER_STATE -> "刷新状态"
    }
}

private fun RoleplayScriptPermission.label(): String {
    return when (this) {
        RoleplayScriptPermission.READ_VARIABLES -> "读取变量"
        RoleplayScriptPermission.WRITE_VARIABLES -> "写入变量"
        RoleplayScriptPermission.MODIFY_PROMPT -> "追加提示"
        RoleplayScriptPermission.MODIFY_OUTGOING_MESSAGE -> "改写发送"
        RoleplayScriptPermission.RENDER_STATE -> "渲染状态"
        RoleplayScriptPermission.WRITE_LOG -> "写日志"
        RoleplayScriptPermission.READ_FILE -> "读文件"
        RoleplayScriptPermission.WRITE_FILE -> "写文件"
        RoleplayScriptPermission.SAVE_IMAGE -> "保存图片"
        RoleplayScriptPermission.EXTERNAL_IMPORT -> "外部导入"
    }
}

private const val MAX_BINDING_OPTIONS_IN_EDITOR = 8
