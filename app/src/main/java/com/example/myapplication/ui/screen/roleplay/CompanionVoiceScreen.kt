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
import com.example.myapplication.roleplay.companion.CompanionSpeechSupport
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.CompanionVoiceUiState

data class CompanionVoiceCallbacks(
    val onSelect: (CompanionSpeechSelection?) -> Unit,
    val onPlay: (CompanionSpeechSelection) -> Unit,
    val onGenerateAndFavorite: (CompanionSpeechSelection) -> Unit,
    val onFavoriteCurrent: () -> Unit,
    val onPlayFavorite: (String) -> Unit,
    val onUnfavorite: (String) -> Unit,
    val onStop: () -> Unit,
    val onRetry: () -> Unit,
    val onReadOriginal: () -> Unit,
    val onRegenerate: () -> Unit,
    val onDraftChange: (CompanionVoicePreferences) -> Unit,
    val onSavePreferences: () -> Unit,
    val onReloadPreferences: () -> Unit,
    val onClearCache: () -> Unit,
    val onOpenLibrary: () -> Unit = {},
)

data class CompanionVoiceBinding(
    val state: CompanionVoiceUiState,
    val callbacks: CompanionVoiceCallbacks
)

@Composable
internal fun CompanionVoiceToolbar(binding: CompanionVoiceBinding) {
    var open by rememberSaveable(binding.state.owner) { mutableStateOf(false) }
    NarraTextButton(
        onClick = { open = true },
        modifier = Modifier.testTag("companion_open_voice")
    ) {
        Text("语音与收藏")
    }
    if (open) CompanionVoiceSelectionDialog(binding) {
        open = false
        binding.callbacks.onStop()
    }
}

@Composable
private fun CompanionVoiceSelectionDialog(binding: CompanionVoiceBinding, onDismiss: () -> Unit) {
    val state = binding.state
    NarraAlertDialog(
        title = "台词与选区朗读",
        onDismiss = onDismiss,
        onConfirm = onDismiss,
        confirmLabel = "关闭",
        dismissLabel = null,
        modifier = Modifier.testTag("companion_voice_selection_dialog"),
        content = {
            Text("选择内容后点播；长内容按句段串行朗读。", style = MaterialTheme.typography.bodySmall)
            CompanionVoiceActions(binding, state.selection)
            if (state.units.isEmpty()) Text("当前没有已保存且可朗读的内容。")
            state.units.forEach { unit ->
                Column(Modifier.fillMaxWidth().testTag("companion_voice_unit_${unit.id}")) {
                    Text(
                        unit.speakerName.ifBlank { if (unit.contentType == RoleplayContentType.DIALOGUE) "台词" else "旁白" },
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(unit.text)
                    NarraTextButton(
                        onClick = {
                            binding.callbacks.onSelect(
                                CompanionSpeechSupport.select(
                                    state.units,
                                    unit.id
                                )
                            )
                        },
                        modifier = Modifier.testTag("companion_voice_select_${unit.id}"),
                    ) { Text("选择这段") }
                }
            }
        },
    )
}

/** 原文预览与声音动作同源，关闭/缺配置时仍能阅读完整选择。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompanionVoiceActions(
    binding: CompanionVoiceBinding,
    selection: CompanionSpeechSelection?,
    showLibraryButton: Boolean = true
) {
    val state = binding.state
    val voice = state.voice
    val callbacks = binding.callbacks
    Column(
        Modifier.fillMaxWidth().testTag("companion_voice_actions"),
        verticalArrangement = Arrangement.spacedBy(NarraUi.space8)
    ) {
        if (!state.preferences.enabled) Text("陪伴语音已关闭。可在语音设置中启用，文字仍可正常阅读。")
        if (selection != null) Text(
            selection.text,
            modifier = Modifier.testTag("companion_voice_selected_text")
        )
        if (voice.phase != CompanionVoicePhase.IDLE) {
            Text(voicePhaseLabel(voice), modifier = Modifier.testTag("companion_voice_phase"))
            if (voice.originalText.isNotEmpty()) {
                Text("原文", style = MaterialTheme.typography.labelLarge)
                Text(
                    voice.originalText,
                    modifier = Modifier.testTag("companion_voice_original_text")
                )
            }
            if (voice.spokenText.isNotEmpty()) {
                Text("朗读文本 · ${voice.languageTag}", style = MaterialTheme.typography.labelLarge)
                Text(voice.spokenText, modifier = Modifier.testTag("companion_voice_spoken_text"))
            }
        }
        voice.errorMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("companion_voice_error")
            )
        }
        voice.noticeMessage?.let { Text(it, modifier = Modifier.testTag("companion_voice_notice")) }
        if (voice.currentAudio?.receipt?.attachmentState == CompanionAudioAttachmentState.PENDING) {
            Text("音频已保存，记录挂接待补存。重试保存会复用这份音频。")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space4)) {
            if (selection != null) {
                NarraTextButton(
                    onClick = { callbacks.onPlay(selection) },
                    enabled = state.preferences.enabled,
                    modifier = Modifier.testTag("companion_voice_play_selection"),
                ) { Text("朗读选区（可能计费）") }
                NarraTextButton(
                    onClick = { callbacks.onGenerateAndFavorite(selection) },
                    enabled = state.preferences.enabled,
                    modifier = Modifier.testTag("companion_voice_generate_favorite"),
                ) { Text("生成并收藏（可能计费）") }
            }
            if (voice.currentAudio != null) {
                NarraTextButton(
                    onClick = callbacks.onFavoriteCurrent,
                    modifier = Modifier.testTag("companion_voice_favorite_existing")
                ) {
                    Text("收藏已有音频")
                }
            }
            if (voice.failureStage == CompanionVoiceFailureStage.TRANSLATION) {
                NarraTextButton(
                    onClick = callbacks.onRetry,
                    modifier = Modifier.testTag("companion_voice_retry_translation")
                ) { Text("重试翻译") }
                NarraTextButton(
                    onClick = callbacks.onReadOriginal,
                    modifier = Modifier.testTag("companion_voice_read_original")
                ) { Text("读原文") }
            } else if (voice.phase == CompanionVoicePhase.ERROR && !voice.needsExplicitRegeneration ||
                voice.currentAudio?.receipt?.attachmentState == CompanionAudioAttachmentState.PENDING
            ) {
                NarraTextButton(
                    onClick = callbacks.onRetry,
                    modifier = Modifier.testTag("companion_voice_retry")
                ) {
                    Text(if (voice.failureStage == CompanionVoiceFailureStage.STORAGE || voice.currentAudio?.receipt?.attachmentState == CompanionAudioAttachmentState.PENDING) "重试保存音频" else "重试朗读")
                }
            }
            if (voice.needsExplicitRegeneration) {
                NarraTextButton(
                    onClick = callbacks.onRegenerate,
                    modifier = Modifier.testTag("companion_voice_regenerate")
                ) {
                    Text("重新生成（可能计费）")
                }
            }
            NarraTextButton(
                onClick = callbacks.onStop,
                modifier = Modifier.testTag("companion_voice_stop")
            ) { Text("停止朗读") }
            if (showLibraryButton) NarraTextButton(
                onClick = callbacks.onOpenLibrary,
                modifier = Modifier.testTag("companion_voice_library")
            ) { Text("语音设置与收藏") }
        }
    }
}

private fun voicePhaseLabel(state: CompanionVoiceState): String {
    val phase = when (state.phase) {
        CompanionVoicePhase.IDLE -> "未开始"
        CompanionVoicePhase.TRANSLATING -> "正在翻译"
        CompanionVoicePhase.SYNTHESIZING -> "正在生成整段音频"
        CompanionVoicePhase.PUBLISHING -> "正在保存音频"
        CompanionVoicePhase.PREPARING -> "正在准备播放"
        CompanionVoicePhase.PLAYING -> "正在播放"
        CompanionVoicePhase.COMPLETED -> "本次朗读已完成"
        CompanionVoicePhase.ERROR -> "朗读未完成"
    }
    return if (state.total > 0) "$phase · ${state.currentIndex + 1}/${state.total}" else phase
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanionVoiceScreen(
    state: CompanionVoiceUiState,
    callbacks: CompanionVoiceCallbacks,
    onBack: () -> Unit,
    onOpenVoiceConfiguration: () -> Unit,
) {
    var confirmDiscard by rememberSaveable(state.owner.characterId) { mutableStateOf(false) }
    var confirmReload by rememberSaveable(state.owner.characterId) { mutableStateOf(false) }
    var confirmClear by rememberSaveable(state.owner.characterId) { mutableStateOf(false) }
    fun requestBack() {
        if (!state.isSaving) {
            if (state.isDirty) confirmDiscard = true else onBack()
        }
    }
    BackHandler(onBack = ::requestBack)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize().testTag("companion_voice_screen")
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = NarraUi.space12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("语音与收藏", style = MaterialTheme.typography.titleLarge)
                    Text(
                        state.characterName.ifBlank { "角色声音" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                NarraTextButton(
                    onClick = ::requestBack,
                    modifier = Modifier.testTag("companion_voice_back")
                ) { Text("返回") }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(NarraUi.space12)
                    .testTag("companion_voice_scroll"),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
            ) {
                Text("复用当前 MiMo 服务与角色音色。陪伴语音与语音消息后台合成分别控制。")
                NarraTextButton(
                    onClick = onOpenVoiceConfiguration,
                    modifier = Modifier.testTag("companion_voice_configuration")
                ) {
                    Text("查看 MiMo 与角色音色配置")
                }
                if (!state.configurationSupported) Text("此声音设置版本暂不支持编辑，已有收藏仍可阅读和播放。")
                VoicePreferenceSwitch(
                    "陪伴语音",
                    "启用后可点播所选台词和旁白",
                    state.draft.enabled,
                    state.editable,
                    "companion_voice_enabled"
                ) {
                    callbacks.onDraftChange(state.draft.copy(enabled = it))
                }
                VoicePreferenceSwitch(
                    "自动朗读新台词",
                    "只读新提交台词，开场不自动朗读",
                    state.draft.autoPlay,
                    state.editable,
                    "companion_voice_auto"
                ) {
                    callbacks.onDraftChange(state.draft.copy(autoPlay = it))
                }
                Text("朗读方式", style = MaterialTheme.typography.titleMedium)
                CompanionVoiceReadMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget)
                            .selectable(
                                state.draft.readMode == mode,
                                state.editable,
                                Role.RadioButton
                            ) { callbacks.onDraftChange(state.draft.copy(readMode = mode)) }
                            .testTag("companion_voice_mode_${mode.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.draft.readMode == mode,
                            onClick = null,
                            enabled = state.editable
                        )
                        Text(if (mode == CompanionVoiceReadMode.ORIGINAL) "读原文" else "先翻译再朗读")
                    }
                }
                OutlinedTextField(
                    value = state.draft.sourceLanguage,
                    onValueChange = { callbacks.onDraftChange(state.draft.copy(sourceLanguage = it)) },
                    label = { Text("原文语言") }, enabled = state.editable, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("companion_voice_source_language"),
                )
                if (state.draft.readMode == CompanionVoiceReadMode.TRANSLATED) {
                    OutlinedTextField(
                        value = state.draft.targetLanguage,
                        onValueChange = { callbacks.onDraftChange(state.draft.copy(targetLanguage = it)) },
                        label = { Text("朗读语言") }, enabled = state.editable, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                            .testTag("companion_voice_target_language"),
                    )
                    Text(
                        "翻译使用已配置的翻译用途模型，可能产生额外费用；翻译失败时由你选择重试或读原文。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("音频缓存上限", style = MaterialTheme.typography.titleMedium)
                Text("当前上限 ${state.draft.cacheLimitBytes / (1024 * 1024)} MiB；只回收未收藏且未在播放的音频。")
                FlowRow {
                    listOf(16L, 64L, 128L, 256L).forEach { mib ->
                        NarraTextButton(
                            onClick = { callbacks.onDraftChange(state.draft.copy(cacheLimitBytes = mib * 1024 * 1024)) },
                            enabled = state.editable,
                            modifier = Modifier.testTag("companion_voice_limit_$mib"),
                        ) { Text("$mib MiB") }
                    }
                }
                Text(
                    "可清缓存 ${audioSizeLabel(state.usage.cacheBytes)} · ${state.usage.cacheCount} 份",
                    modifier = Modifier.testTag("companion_voice_cache_usage")
                )
                Text(
                    "收藏音频 ${audioSizeLabel(state.usage.favoriteBytes)} · ${state.usage.favoriteCount} 项",
                    modifier = Modifier.testTag("companion_voice_favorite_usage")
                )
                if (state.usage.pendingCount > 0) Text("${state.usage.pendingCount} 份音频记录待处理；已保存音频可免费补存。")
                NarraTextButton(
                    onClick = { confirmClear = true },
                    enabled = !state.isClearingCache,
                    modifier = Modifier.testTag("companion_voice_clear_cache")
                ) {
                    Text(if (state.isClearingCache) "正在清理…" else "清理未收藏的缓存")
                }
                state.libraryError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("companion_voice_library_error")
                    )
                }
                CompanionVoiceActions(
                    CompanionVoiceBinding(state, callbacks),
                    state.selection,
                    showLibraryButton = false
                )
                Text("声音收藏", style = MaterialTheme.typography.titleMedium)
                if (state.favorites.isEmpty()) Text("尚无声音收藏。朗读后可收藏已有音频，或明确选择生成并收藏。")
                state.favorites.forEach { favorite ->
                    val clip = favorite.audio.clip
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                            .testTag("companion_voice_favorite_${favorite.id}")
                    ) {
                        Column(
                            Modifier.padding(NarraUi.space12),
                            verticalArrangement = Arrangement.spacedBy(NarraUi.space8)
                        ) {
                            Text(
                                clip.segment.unit.speakerName.ifBlank { "声音收藏" },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text("原文：${clip.segment.originalText}")
                            Text("朗读 · ${clip.languageTag}：${clip.spokenText}")
                            if (favorite.sourceState == CompanionVoiceSourceState.UNAVAILABLE) Text(
                                "出处已失效，收藏音频仍保留。",
                                modifier = Modifier.testTag("companion_voice_source_unavailable_${favorite.id}")
                            )
                            FlowRow {
                                NarraTextButton(
                                    onClick = { callbacks.onPlayFavorite(favorite.id) },
                                    modifier = Modifier.testTag("companion_voice_play_favorite_${favorite.id}")
                                ) { Text("播放已有声音") }
                                NarraTextButton(
                                    onClick = { callbacks.onUnfavorite(favorite.id) },
                                    modifier = Modifier.testTag("companion_voice_unfavorite_${favorite.id}")
                                ) { Text("取消收藏") }
                            }
                        }
                    }
                }
                Text(
                    "当前为整段音频响应与分段串行朗读。界面显示实际朗读片段，不提供逐字音频进度。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = NarraUi.space12)) {
                state.settingsError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("companion_voice_settings_error")
                    )
                }
                if (state.hasConflict) Text("设置已更新，当前草稿仍保留。")
                FlowRow {
                    NarraTextButton(
                        onClick = callbacks.onSavePreferences,
                        enabled = state.editable && state.isDirty,
                        modifier = Modifier.testTag("companion_voice_save")
                    ) {
                        Text(if (state.isSaving) "正在保存…" else "保存声音偏好")
                    }
                    NarraTextButton(
                        onClick = { confirmReload = true },
                        enabled = !state.isSaving,
                        modifier = Modifier.testTag("companion_voice_reload")
                    ) {
                        Text("放弃草稿并读取最新")
                    }
                }
            }
        }
    }
    if (confirmDiscard) NarraAlertDialog(
        title = "放弃声音偏好草稿？",
        message = "未保存的选择会被放弃，原文和声音收藏保留。",
        onDismiss = { confirmDiscard = false },
        onConfirm = { confirmDiscard = false; callbacks.onReloadPreferences(); onBack() },
        confirmLabel = "放弃并返回",
    )
    if (confirmReload) NarraAlertDialog(
        title = "读取最新声音偏好？",
        message = "这会放弃当前未保存的草稿。",
        onDismiss = { confirmReload = false },
        onConfirm = { confirmReload = false; callbacks.onReloadPreferences() },
        confirmLabel = "放弃并读取",
    )
    if (confirmClear) NarraAlertDialog(
        title = "清理未收藏的音频缓存？",
        message = "声音收藏、正在使用的音频和原文会保留。再次朗读被清理的内容可能需要重新生成。",
        onDismiss = { confirmClear = false },
        onConfirm = { confirmClear = false; callbacks.onClearCache() },
        confirmLabel = "清理缓存",
    )
}

@Composable
private fun VoicePreferenceSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title); Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall
        )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private fun audioSizeLabel(bytes: Long): String =
    if (bytes < 1024 * 1024) "${bytes / 1024} KiB" else "${bytes / (1024 * 1024)} MiB"
