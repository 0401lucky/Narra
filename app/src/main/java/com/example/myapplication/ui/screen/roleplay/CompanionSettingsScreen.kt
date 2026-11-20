package com.example.myapplication.ui.screen.roleplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.example.myapplication.model.*
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.screen.settings.SettingsHelpButton
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.CompanionSettingsUiState

data class CompanionSettingsCallbacks(
    val onWritingChange: (CompanionWritingSettings) -> Unit,
    val onZoneChange: (String) -> Unit,
    val onAddCustomField: () -> Unit,
    val onAddTemplate: (String) -> Unit,
    val onSave: () -> Unit,
    val onBack: () -> Unit,
    val onDismissDiscard: () -> Unit,
    val onDiscard: () -> Unit,
    val onReload: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanionSettingsScreen(
    state: CompanionSettingsUiState,
    callbacks: CompanionSettingsCallbacks
) {
    var templateName by rememberSaveable(state.assistantId) { mutableStateOf("") }
    var showReload by rememberSaveable(state.assistantId) { mutableStateOf(false) }
    BackHandler(onBack = callbacks.onBack)
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.fillMaxWidth().padding(NarraUi.space16)) {
                Text("陪伴表达", style = MaterialTheme.typography.headlineSmall)
                Text(
                    state.characterName.ifBlank { "当前角色" },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .testTag("companion_settings_scroll").padding(horizontal = NarraUi.space16),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
            ) {
                if (state.isLoading) CircularProgressIndicator()
                if (state.draft?.schemaVersion != null && state.draft.schemaVersion != 1) {
                    Text(
                        "此角色的陪伴设置需要较新版本，当前只读。",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                val draft = state.draft
                if (draft != null) {
                    val writing = draft.writing
                    SettingsHelpButton(
                        title = "陪伴表达的作用",
                        description = "保存后用于后续陪伴回复。文风与人称控制叙述方式，台词保持正常说话；开场始终使用第三人称。已保存回复按当时的观测定义显示。",
                    )
                    Text("文风", style = MaterialTheme.typography.titleMedium)
                    CompanionChoice(
                        "不指定，沿用已有写法",
                        writing.style == null,
                        state.editable,
                        "companion_style_default"
                    ) {
                        callbacks.onWritingChange(writing.copy(style = null))
                    }
                    CompanionWritingStyle.entries.forEach { style ->
                        CompanionChoice(
                            style.label,
                            writing.style == style,
                            state.editable,
                            "companion_style_${style.name}"
                        ) {
                            callbacks.onWritingChange(writing.copy(style = style))
                        }
                    }
                    Text("叙述人称", style = MaterialTheme.typography.titleMedium)
                    CompanionChoice(
                        "不指定",
                        writing.pointOfView == null,
                        state.editable,
                        "companion_pov_default"
                    ) {
                        callbacks.onWritingChange(writing.copy(pointOfView = null))
                    }
                    CompanionPointOfView.entries.forEach { pointOfView ->
                        CompanionChoice(
                            pointOfView.label,
                            writing.pointOfView == pointOfView,
                            state.editable,
                            "companion_pov_${pointOfView.name}"
                        ) {
                            callbacks.onWritingChange(writing.copy(pointOfView = pointOfView))
                        }
                    }
                    CompanionToggle(
                        "细节线索",
                        writing.detailHintsEnabled,
                        state.editable,
                        "companion_detail_hints"
                    ) {
                        callbacks.onWritingChange(writing.copy(detailHintsEnabled = it))
                    }
                    OutlinedTextField(
                        value = writing.extraStyle,
                        onValueChange = { callbacks.onWritingChange(writing.copy(extraStyle = it)) },
                        label = { Text("自定义文风补充") },
                        supportingText = { Text("${writing.extraStyle.length} / ${CompanionWritingValidation.MAX_EXTRA_STYLE}") },
                        enabled = state.editable, minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("companion_extra_style"),
                    )
                    Text("文风模板", style = MaterialTheme.typography.titleMedium)
                    writing.templates.forEach { template ->
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(NarraUi.space8)
                        ) {
                            OutlinedTextField(
                                value = template.name,
                                enabled = state.editable,
                                onValueChange = { name ->
                                    callbacks.onWritingChange(
                                        writing.copy(
                                            templates = writing.templates.map {
                                                if (it.id == template.id) it.copy(
                                                    name = name
                                                ) else it
                                            },
                                        )
                                    )
                                },
                                label = { Text("模板名称") },
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("companion_template_${template.id}"),
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                                NarraTextButton(onClick = {
                                    callbacks.onWritingChange(
                                        writing.copy(
                                            extraStyle = template.text
                                        )
                                    )
                                }, enabled = state.editable) { Text("应用模板") }
                                NarraTextButton(onClick = {
                                    callbacks.onWritingChange(
                                        writing.copy(
                                            templates = writing.templates.map {
                                                if (it.id == template.id) it.copy(
                                                    text = writing.extraStyle
                                                ) else it
                                            },
                                        )
                                    )
                                }, enabled = state.editable) { Text("用当前补充更新") }
                                NarraTextButton(onClick = {
                                    callbacks.onWritingChange(
                                        writing.copy(
                                            templates = writing.templates.filterNot { it.id == template.id })
                                    )
                                }, enabled = state.editable) { Text("删除模板") }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        enabled = state.editable,
                        label = { Text("新模板名称") },
                        modifier = Modifier.fillMaxWidth().testTag("companion_template_name"),
                    )
                    NarraTextButton(
                        onClick = { callbacks.onAddTemplate(templateName) },
                        enabled = state.editable && templateName.isNotBlank()
                    ) {
                        Text("将当前补充存为模板")
                    }
                    Text("回复观测", style = MaterialTheme.typography.titleMedium)
                    CompanionToggle(
                        "显示回复观测",
                        writing.observation.enabled,
                        state.editable,
                        "companion_observation_enabled"
                    ) {
                        callbacks.onWritingChange(
                            writing.copy(
                                observation = writing.observation.copy(
                                    enabled = it
                                )
                            )
                        )
                    }
                    Text(
                        "观测来自同一条回复；缺失时不会沿用上一条。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (writing.observation.enabled) {
                        CompanionObservationStyle.entries.forEach { style ->
                            CompanionChoice(
                                style.label,
                                writing.observation.style == style,
                                state.editable,
                                "companion_observation_style_${style.name}"
                            ) {
                                callbacks.onWritingChange(
                                    writing.copy(
                                        observation = writing.observation.copy(
                                            style = style
                                        )
                                    )
                                )
                            }
                        }
                    }
                    (writing.observation.fields + writing.observation.customFields).forEach { field ->
                        fun replaceField(updated: CompanionObservationField) {
                            callbacks.onWritingChange(
                                writing.copy(
                                    observation = writing.observation.copy(
                                        fields = writing.observation.fields.map { if (it.id == field.id) updated else it },
                                        customFields = writing.observation.customFields.map { if (it.id == field.id) updated else it },
                                    )
                                )
                            )
                        }
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(NarraUi.space8)
                        ) {
                            OutlinedTextField(
                                value = field.label,
                                onValueChange = { replaceField(field.copy(label = it)) },
                                enabled = state.editable,
                                label = { Text("观测名称") },
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("companion_field_label_${field.id}"),
                            )
                            CompanionToggle(
                                "启用此项",
                                field.enabled,
                                state.editable,
                                "companion_field_enabled_${field.id}"
                            ) { replaceField(field.copy(enabled = it)) }
                            if (field in writing.observation.customFields) {
                                NarraTextButton(
                                    onClick = {
                                        callbacks.onWritingChange(
                                            writing.copy(
                                                observation = writing.observation.copy(
                                                    customFields = writing.observation.customFields.filterNot { it.id == field.id },
                                                )
                                            )
                                        )
                                    },
                                    enabled = state.editable,
                                    modifier = Modifier.testTag("companion_field_delete_${field.id}"),
                                ) { Text("删除此观测项") }
                            }
                        }
                    }
                    NarraTextButton(
                        onClick = callbacks.onAddCustomField,
                        enabled = state.editable && writing.observation.customFields.size < CompanionWritingValidation.MAX_CUSTOM_FIELDS,
                        modifier = Modifier.testTag("companion_add_observation"),
                    ) { Text("添加自定义观测（${writing.observation.customFields.size}/6）") }
                    OutlinedTextField(
                        value = draft.zoneId, onValueChange = callbacks.onZoneChange,
                        label = { Text("角色时区（可留空）") }, enabled = state.editable,
                        supportingText = { Text("例如 Asia/Shanghai；留空使用设备时区。由场景的时间感知开关控制是否使用。") },
                        modifier = Modifier.fillMaxWidth().testTag("companion_settings_zone"),
                    )
                    Spacer(Modifier.height(NarraUi.space16))
                }
            }
            Column(Modifier.fillMaxWidth().padding(NarraUi.space12)) {
                state.errorMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("companion_settings_error")
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                    NarraTextButton(
                        onClick = callbacks.onSave,
                        enabled = state.editable,
                        modifier = Modifier.testTag("companion_settings_save")
                    ) {
                        Text(if (state.isSaving) "保存中…" else "保存")
                    }
                    NarraTextButton(
                        onClick = callbacks.onBack,
                        enabled = !state.isSaving,
                        modifier = Modifier.testTag("companion_settings_cancel")
                    ) { Text("取消") }
                    if (state.hasConflict) NarraTextButton(
                        onClick = { showReload = true },
                        enabled = !state.isSaving
                    ) {
                        Text("读取最新设置")
                    }
                }
            }
        }
    }
    if (state.showDiscardConfirmation) NarraAlertDialog(
        title = "放弃未保存的修改？", message = "已保存的陪伴设置保持原样。",
        onDismiss = callbacks.onDismissDiscard, onConfirm = callbacks.onDiscard,
        confirmLabel = "放弃修改", dismissLabel = "继续编辑",
    )
    if (showReload) NarraAlertDialog(
        title = "读取最新设置？",
        message = "这会放弃本页尚未保存的草稿。",
        onDismiss = { showReload = false },
        onConfirm = { showReload = false; callbacks.onReload() },
        confirmLabel = "放弃草稿并读取",
        dismissLabel = "保留草稿",
    )
}

@Composable
private fun CompanionChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            label,
            Modifier.weight(1f).padding(start = NarraUi.space8),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun CompanionToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
