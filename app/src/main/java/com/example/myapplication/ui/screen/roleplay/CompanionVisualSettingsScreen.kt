package com.example.myapplication.ui.screen.roleplay

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import com.example.myapplication.model.*
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.CompanionVisualSettingsUiState

data class CompanionVisualSettingsCallbacks(
    val onDraftChange: (CompanionVisualSettings) -> Unit,
    val onImportImage: (String, CompanionImageKind, String) -> Unit,
    val onAddOutfit: () -> Unit,
    val onRemoveResource: (String) -> Unit,
    val onSave: () -> Unit,
    val onBack: () -> Unit,
    val onDismissDiscard: () -> Unit,
    val onDiscard: () -> Unit,
    val onReload: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanionVisualSettingsScreen(
    state: CompanionVisualSettingsUiState,
    callbacks: CompanionVisualSettingsCallbacks
) {
    var importKind by rememberSaveable { mutableStateOf(CompanionImageKind.PORTRAIT) }
    var reloadDialog by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            callbacks.onImportImage(
                it.toString(),
                importKind,
                "${importKind.label} ${(state.draft?.resources?.size ?: 0) + 1}"
            )
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    fun back() {
        if (imeVisible) {
            keyboard?.hide(); focus.clearFocus()
        } else callbacks.onBack()
    }
    BackHandler(onBack = ::back)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize().testTag("companion_visual_settings")
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            val feedbackHeight = maxHeight * 0.24f
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = NarraUi.space12),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("演出素材与布局", style = MaterialTheme.typography.titleLarge)
                        Text(
                            state.characterName.ifBlank { "当前角色" },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    NarraTextButton(
                        onClick = ::back,
                        modifier = Modifier.testTag("companion_visual_back")
                    ) { Text("返回") }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .testTag("companion_visual_scroll").padding(horizontal = NarraUi.space12),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space12)
                ) {
                    if (state.isLoading) CircularProgressIndicator()
                    val draft = state.draft
                    if (draft != null) {
                        Text("显示偏好", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                            NarraTextButton(
                                onClick = { callbacks.onDraftChange(draft.copy(preferredMode = null)) },
                                enabled = state.editable,
                                modifier = Modifier.testTag("companion_visual_mode_default")
                            ) { Text("沿用原列表") }
                            CompanionDisplayMode.entries.forEach { mode ->
                                NarraTextButton(
                                    onClick = { callbacks.onDraftChange(draft.copy(preferredMode = mode)) },
                                    enabled = state.editable,
                                    modifier = Modifier.testTag("companion_visual_mode_${mode.name}")
                                ) { Text(mode.label) }
                            }
                        }
                        Text("图片素材", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "立绘用于角色主体，背景用于场景，装饰不会替代角色。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                            CompanionImageKind.entries.forEach { kind ->
                                NarraTextButton(
                                    onClick = { importKind = kind; picker.launch("image/*") },
                                    enabled = state.editable,
                                    modifier = Modifier.testTag("companion_visual_import_${kind.name}")
                                ) { Text("导入${kind.label}") }
                            }
                        }
                        if (state.isImporting) Text("正在导入图片…")
                        draft.resources.forEach { resource ->
                            key(resource.id) {
                                Surface(
                                    shape = NarraShapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("companion_visual_resource_${resource.id}")
                                ) {
                                    Column(
                                        Modifier.padding(NarraUi.space12),
                                        verticalArrangement = Arrangement.spacedBy(NarraUi.space8)
                                    ) {
                                        CompanionStageImage(
                                            listOf(
                                                CompanionResolvedImage(
                                                    resource.id,
                                                    resource.path,
                                                    kind = resource.kind
                                                )
                                            ),
                                            resource.name,
                                            Modifier.fillMaxWidth()
                                                .height(NarraMinimumTouchTarget * 2),
                                            ContentScale.Fit,
                                            "图片暂不可读"
                                        )
                                        OutlinedTextField(
                                            resource.name,
                                            onValueChange = { name ->
                                                callbacks.onDraftChange(
                                                    draft.copy(
                                                        resources =
                                                        draft.resources.map {
                                                            if (it.id == resource.id) it.copy(
                                                                name = name
                                                            ) else it
                                                        })
                                                )
                                            },
                                            enabled = state.editable,
                                            label = { Text("图片名称") },
                                            modifier = Modifier.fillMaxWidth()
                                                .testTag("companion_visual_resource_name_${resource.id}")
                                        )
                                        Text(
                                            "${resource.kind.label} · ${resource.width} × ${resource.height}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        NarraTextButton(
                                            onClick = { callbacks.onRemoveResource(resource.id) },
                                            enabled = state.editable,
                                            modifier = Modifier.testTag("companion_visual_remove_resource_${resource.id}")
                                        ) { Text("移除此素材引用") }
                                    }
                                }
                            }
                        }
                        Text("角色套装与表情图集", style = MaterialTheme.typography.titleMedium)
                        NarraTextButton(
                            onClick = callbacks.onAddOutfit,
                            enabled = state.editable && draft.outfits.size < CompanionVisualValidation.MAX_OUTFITS,
                            modifier = Modifier.testTag("companion_visual_add_outfit")
                        ) { Text("新增套装") }
                        val portraits =
                            listOf(null to "缺图时沿用旧图") + draft.resources.filter { it.kind == CompanionImageKind.PORTRAIT }
                                .map { it.id to it.name }
                        draft.outfits.forEach { outfit ->
                            key(outfit.id) {
                                var expression by rememberSaveable(outfit.id) { mutableStateOf("") }
                                var expressionImage by rememberSaveable(outfit.id) {
                                    mutableStateOf<String?>(
                                        null
                                    )
                                }
                                Surface(
                                    shape = NarraShapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("companion_visual_outfit_${outfit.id}")
                                ) {
                                    Column(
                                        Modifier.padding(NarraUi.space12),
                                        verticalArrangement = Arrangement.spacedBy(NarraUi.space8)
                                    ) {
                                        fun updateOutfit(next: CompanionOutfit) =
                                            callbacks.onDraftChange(draft.copy(outfits = draft.outfits.map { if (it.id == next.id) next else it }))
                                        OutlinedTextField(
                                            outfit.name,
                                            { updateOutfit(outfit.copy(name = it)) },
                                            enabled = state.editable,
                                            label = { Text("套装名称") },
                                            modifier = Modifier.fillMaxWidth()
                                                .testTag("companion_visual_outfit_name_${outfit.id}")
                                        )
                                        CompanionReferencePicker(
                                            "默认立绘",
                                            portraits,
                                            outfit.defaultPortraitId,
                                            state.editable,
                                            "companion_visual_outfit_default_${outfit.id}"
                                        ) { updateOutfit(outfit.copy(defaultPortraitId = it)) }
                                        outfit.expressions.forEach { (key, reference) ->
                                            CompanionReferencePicker(
                                                "表情：$key", portraits, reference, state.editable,
                                                "companion_visual_expression_${outfit.id}_$key"
                                            ) { next ->
                                                updateOutfit(
                                                    outfit.copy(
                                                        expressions =
                                                            if (next == null) outfit.expressions - key else outfit.expressions + (key to next)
                                                    )
                                                )
                                            }
                                        }
                                        OutlinedTextField(
                                            expression,
                                            { expression = it },
                                            enabled = state.editable,
                                            label = { Text("新表情键，例如 happy") },
                                            modifier = Modifier.fillMaxWidth()
                                                .testTag("companion_visual_expression_key_${outfit.id}")
                                        )
                                        CompanionReferencePicker(
                                            "新表情图片",
                                            portraits,
                                            expressionImage,
                                            state.editable,
                                            "companion_visual_expression_image_${outfit.id}"
                                        ) { expressionImage = it }
                                        val normalizedKey =
                                            CompanionExpressionKeys.normalize(expression)
                                        if (expression.isNotBlank() && normalizedKey == null) Text(
                                            "表情键请用英文小写、数字、下划线或短横线。",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        NarraTextButton(
                                            onClick = {
                                                updateOutfit(outfit.copy(expressions = outfit.expressions + (normalizedKey!! to expressionImage!!))); expression =
                                                ""; expressionImage = null
                                            },
                                            enabled = state.editable && normalizedKey != null && expressionImage != null && outfit.expressions.size < CompanionVisualValidation.MAX_EXPRESSIONS,
                                            modifier = Modifier.testTag("companion_visual_add_expression_${outfit.id}")
                                        ) { Text("加入表情图集") }
                                        NarraTextButton(
                                            onClick = {
                                                callbacks.onDraftChange(
                                                    draft.copy(
                                                        outfits = draft.outfits.filterNot { it.id == outfit.id },
                                                        selectedOutfitId = draft.selectedOutfitId?.takeUnless { it == outfit.id },
                                                        sceneOverrides = draft.sceneOverrides.mapValues {
                                                            it.value.copy(
                                                                selectedOutfitId = it.value.selectedOutfitId?.takeUnless { ref -> ref == outfit.id })
                                                        })
                                                )
                                            },
                                            enabled = state.editable,
                                            modifier = Modifier.testTag("companion_visual_remove_outfit_${outfit.id}")
                                        ) { Text("移除套装") }
                                    }
                                }
                            }
                        }
                        val outfits =
                            listOf(null to "沿用旧场景立绘") + draft.outfits.map { it.id to it.name }
                        val backgrounds =
                            listOf(null to "沿用旧场景背景") + draft.resources.filter { it.kind == CompanionImageKind.BACKGROUND }
                                .map { it.id to it.name }
                        Text("角色默认外观", style = MaterialTheme.typography.titleMedium)
                        CompanionReferencePicker(
                            "默认套装",
                            outfits,
                            draft.selectedOutfitId,
                            state.editable,
                            "companion_visual_default_outfit"
                        ) {
                            callbacks.onDraftChange(draft.copy(selectedOutfitId = it))
                        }
                        CompanionReferencePicker(
                            "默认背景",
                            backgrounds,
                            draft.selectedBackgroundId,
                            state.editable,
                            "companion_visual_default_background"
                        ) {
                            callbacks.onDraftChange(draft.copy(selectedBackgroundId = it))
                        }
                        CompanionLayoutChoices(
                            draft.layout,
                            state.editable,
                            "companion_visual_layout"
                        ) { callbacks.onDraftChange(draft.copy(layout = it)) }
                        Text("本场外观", style = MaterialTheme.typography.titleMedium)
                        val current = draft.sceneOverrides[state.scenarioId]
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget)
                                .clickable(enabled = state.editable) {
                                    callbacks.onDraftChange(
                                        draft.copy(
                                            sceneOverrides = if (current != null) draft.sceneOverrides - state.scenarioId else
                                                draft.sceneOverrides + (state.scenarioId to CompanionSceneVisualOverride(
                                                    draft.selectedOutfitId,
                                                    draft.selectedBackgroundId,
                                                    draft.layout
                                                ))
                                        )
                                    )
                                }.testTag("companion_visual_scene_override"),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Checkbox(
                                current != null,
                                onCheckedChange = null,
                                enabled = state.editable
                            )
                            Text("为本场单独选择套装、背景与布局", modifier = Modifier.weight(1f))
                        }
                        if (current != null) {
                            fun changeScene(next: CompanionSceneVisualOverride) =
                                callbacks.onDraftChange(draft.copy(sceneOverrides = draft.sceneOverrides + (state.scenarioId to next)))
                            CompanionReferencePicker(
                                "本场套装",
                                outfits,
                                current.selectedOutfitId,
                                state.editable,
                                "companion_visual_scene_outfit"
                            ) { changeScene(current.copy(selectedOutfitId = it)) }
                            CompanionReferencePicker(
                                "本场背景",
                                backgrounds,
                                current.selectedBackgroundId,
                                state.editable,
                                "companion_visual_scene_background"
                            ) { changeScene(current.copy(selectedBackgroundId = it)) }
                            CompanionLayoutChoices(
                                current.layout,
                                state.editable,
                                "companion_visual_scene_layout"
                            ) { changeScene(current.copy(layout = it)) }
                        }
                        if (draft.resources.any { it.kind == CompanionImageKind.DECOR }) {
                            Text("场景装饰", style = MaterialTheme.typography.titleMedium)
                            draft.resources.filter { it.kind == CompanionImageKind.DECOR }
                                .forEach { decor ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .heightIn(min = NarraMinimumTouchTarget)
                                            .clickable(enabled = state.editable) {
                                                callbacks.onDraftChange(draft.copy(decorationIds = if (decor.id in draft.decorationIds) draft.decorationIds - decor.id else draft.decorationIds + decor.id))
                                            }.testTag("companion_visual_decor_${decor.id}"),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            decor.id in draft.decorationIds,
                                            null,
                                            enabled = state.editable
                                        )
                                        Text(decor.name)
                                    }
                                }
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().heightIn(max = feedbackHeight)
                        .verticalScroll(rememberScrollState()).padding(horizontal = NarraUi.space12)
                ) {
                    if (!state.settingsVersionSupported || (state.draft != null && state.draft.schemaVersion != 1)) Text(
                        "此设置需要较新版本，当前只读。",
                        color = MaterialTheme.colorScheme.error
                    )
                    if (state.hasConflict) Text(
                        "设置已在别处更新，未保存的草稿仍保留。",
                        color = MaterialTheme.colorScheme.error
                    )
                    state.errorMessage?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("companion_visual_error")
                        )
                    }
                    if (state.hasConflict) NarraTextButton(
                        onClick = { reloadDialog = true },
                        enabled = !state.isSaving && !state.isImporting,
                        modifier = Modifier.testTag("companion_visual_reload")
                    ) { Text("读取最新设置") }
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = NarraUi.space12),
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)
                ) {
                    NarraTextButton(
                        onClick = callbacks.onSave, enabled = state.editable,
                        modifier = Modifier.testTag("companion_visual_save")
                    ) { Text(if (state.isSaving) "正在保存…" else "保存") }
                    NarraTextButton(
                        onClick = ::back, enabled = !state.isSaving,
                        modifier = Modifier.testTag("companion_visual_cancel")
                    ) { Text("取消") }
                }
            }
        }
    }
    if (state.showDiscardConfirmation) NarraAlertDialog(
        title = "放弃未保存的外观？",
        message = "已保存的外观与共享素材会保留。",
        onDismiss = callbacks.onDismissDiscard,
        onConfirm = callbacks.onDiscard,
        confirmLabel = "放弃草稿",
        dismissLabel = "继续编辑"
    )
    if (reloadDialog) NarraAlertDialog(
        title = "读取最新设置？",
        message = "当前未保存的草稿将被放弃。",
        onDismiss = { reloadDialog = false },
        onConfirm = { reloadDialog = false; callbacks.onReload() },
        confirmLabel = "读取最新",
        dismissLabel = "继续编辑"
    )
}

@Composable
private fun CompanionReferencePicker(
    label: String, options: List<Pair<String?, String>>, selected: String?, enabled: Boolean,
    tag: String, onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            NarraTextButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.testTag(tag)
            ) {
                Text(options.firstOrNull { it.first == selected }?.second ?: "沿用旧图")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { onSelect(id); expanded = false },
                        modifier = Modifier.testTag("${tag}_option_${id ?: "none"}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanionLayoutChoices(
    layout: CompanionStageLayout,
    enabled: Boolean,
    tag: String,
    onChange: (CompanionStageLayout) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(NarraUi.space4)) {
        Text("立绘位置与大小", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space4)) {
            CompanionPortraitPosition.entries.forEach { position ->
                NarraTextButton(
                    onClick = { onChange(layout.copy(portraitPosition = position)) },
                    enabled = enabled, modifier = Modifier.testTag("${tag}_${position.name}")
                ) { Text(position.label) }
            }
        }
        Text(
            "宽度 ${(layout.portraitWidth * 100).toInt()}% · 高度 ${(layout.portraitHeight * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space4)) {
            listOf(0.5f, 0.75f, 1f).forEach { width ->
                NarraTextButton(
                    onClick = { onChange(layout.copy(portraitWidth = width)) }, enabled = enabled,
                    modifier = Modifier.testTag("${tag}_width_${(width * 100).toInt()}")
                ) { Text("宽 ${(width * 100).toInt()}%") }
            }
            listOf(0.25f, 0.45f, 0.6f).forEach { height ->
                NarraTextButton(
                    onClick = { onChange(layout.copy(portraitHeight = height)) }, enabled = enabled,
                    modifier = Modifier.testTag("${tag}_height_${(height * 100).toInt()}")
                ) { Text("高 ${(height * 100).toInt()}%") }
            }
        }
    }
}
