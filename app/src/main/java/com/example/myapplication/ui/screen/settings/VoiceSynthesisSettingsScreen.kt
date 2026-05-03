package com.example.myapplication.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.repository.VoiceCloneSampleStorage
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.MIMO_DEFAULT_VOICE_ID
import com.example.myapplication.model.MIMO_PRESET_VOICES
import com.example.myapplication.model.MIMO_STANDARD_BASE_URL
import com.example.myapplication.model.MIMO_TTS_MODEL_PRESET
import com.example.myapplication.model.MIMO_TTS_MODEL_VOICE_DESIGN
import com.example.myapplication.model.MIMO_TOKEN_PLAN_BASE_URL
import com.example.myapplication.model.VoiceProfile
import com.example.myapplication.model.VoiceProfileMode
import com.example.myapplication.model.VoiceSynthesisSettings
import com.example.myapplication.model.buildVoiceDesignPromptFromAssistant
import com.example.myapplication.model.resolveMimoChatCompletionsEndpoint
import com.example.myapplication.ui.component.NarraButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSynthesisSettingsScreen(
    settings: VoiceSynthesisSettings,
    assistants: List<Assistant>,
    uiMessage: String?,
    isTesting: Boolean,
    onUpdateSettings: (VoiceSynthesisSettings) -> Unit,
    onTestSettings: (VoiceSynthesisSettings) -> Unit,
    onSaveChanges: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    var showDefaultVoiceSheet by rememberSaveable { mutableStateOf(false) }
    var showDefaultModelSheet by rememberSaveable { mutableStateOf(false) }
    var editingAssistantId by rememberSaveable { mutableStateOf<String?>(null) }
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    val normalizedSettings = settings.normalized()
    val selectedAssistant = assistants.firstOrNull { it.id == editingAssistantId }
    val defaultProfile = normalizedSettings.defaultProfile.normalized()
    val defaultModelId = defaultProfile.ttsModel()

    BackHandler {
        onSaveChanges()
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "MiMo 语音",
                subtitle = "语音消息真实播放",
                onNavigateBack = {
                    onSaveChanges()
                    onNavigateBack()
                },
                actionLabel = "保存",
                onAction = onSaveChanges,
            )
        },
        containerColor = palette.background,
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
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    SettingsNoticeCard(
                        title = "真实语音消息",
                        body = "支持 MiMo 官方入口、Token Plan 专属入口和第三方中转。填写 /v1 会自动请求 /chat/completions，失败时仍显示原文字。",
                        containerColor = palette.accent,
                        contentColor = palette.accent,
                    )
                }

                item {
                    SettingsGroup(title = "全局配置") {
                        VoiceSwitchRow(
                            title = "启用 MiMo TTS",
                            supportingText = if (normalizedSettings.enabled) "后台自动合成角色语音消息" else "关闭时只显示文字语音卡片",
                            checked = normalizedSettings.enabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings(normalizedSettings.copy(enabled = enabled))
                            },
                        )
                        SettingsGroupDivider()
                        OutlinedTextField(
                            value = normalizedSettings.apiKey,
                            onValueChange = { apiKey ->
                                onUpdateSettings(normalizedSettings.copy(apiKey = apiKey))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            label = { Text("MiMo API Key") },
                            singleLine = true,
                            visualTransformation = if (showApiKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            supportingText = {
                                Text(
                                    text = if (normalizedSettings.apiKey.isBlank()) {
                                        "未填写，语音消息会保留文字兜底"
                                    } else {
                                        "当前已填写 ${normalizedSettings.apiKey.length} 位，可点右侧图标核对"
                                    },
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) {
                                            Icons.Outlined.VisibilityOff
                                        } else {
                                            Icons.Outlined.Visibility
                                        },
                                        contentDescription = if (showApiKey) "隐藏 Key" else "显示 Key",
                                    )
                                }
                            },
                            colors = rememberSettingsOutlineColors(),
                        )
                        SettingsGroupDivider()
                        OutlinedTextField(
                            value = normalizedSettings.baseUrl,
                            onValueChange = { baseUrl ->
                                onUpdateSettings(normalizedSettings.copy(baseUrl = baseUrl))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            label = { Text("Base URL") },
                            singleLine = true,
                            supportingText = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("可填 ${MIMO_TOKEN_PLAN_BASE_URL}，也可填第三方中转的 /v1 或完整 /chat/completions。")
                                    Text("当前请求：${resolveMimoChatCompletionsEndpoint(normalizedSettings.baseUrl)}")
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            onClick = {
                                                onUpdateSettings(
                                                    normalizedSettings.copy(baseUrl = MIMO_TOKEN_PLAN_BASE_URL),
                                                )
                                            },
                                        ) {
                                            Text("Token Plan")
                                        }
                                        TextButton(
                                            onClick = {
                                                onUpdateSettings(
                                                    normalizedSettings.copy(baseUrl = MIMO_STANDARD_BASE_URL),
                                                )
                                            },
                                        ) {
                                            Text("普通 MiMo")
                                        }
                                    }
                                }
                            },
                            colors = rememberSettingsOutlineColors(),
                        )
                        SettingsGroupDivider()
                        SettingsListRow(
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = palette.title,
                                )
                            },
                            title = "默认合成模型",
                            supportingText = when (defaultProfile.mode) {
                                VoiceProfileMode.VOICE_DESIGN -> "$MIMO_TTS_MODEL_VOICE_DESIGN · 文本设计音色"
                                else -> "$MIMO_TTS_MODEL_PRESET · 预置音色"
                            },
                            onClick = { showDefaultModelSheet = true },
                        )
                        if (defaultProfile.mode == VoiceProfileMode.PRESET) {
                            SettingsGroupDivider()
                            SettingsListRow(
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = palette.title,
                                    )
                                },
                                title = "默认预置音色",
                                supportingText = defaultProfile.presetVoiceId
                                    .ifBlank { MIMO_DEFAULT_VOICE_ID },
                                onClick = { showDefaultVoiceSheet = true },
                            )
                        }
                        if (defaultProfile.mode == VoiceProfileMode.VOICE_DESIGN) {
                            SettingsGroupDivider()
                            OutlinedTextField(
                                value = defaultProfile.voiceDesignPrompt,
                                onValueChange = { prompt ->
                                    onUpdateSettings(
                                        normalizedSettings.copy(
                                            defaultProfile = defaultProfile.copy(
                                                mode = VoiceProfileMode.VOICE_DESIGN,
                                                voiceDesignPrompt = prompt,
                                            ),
                                        ),
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                label = { Text("默认音色设计描述") },
                                minLines = 3,
                                supportingText = {
                                    Text("使用 $MIMO_TTS_MODEL_VOICE_DESIGN 时，会把这段描述放入 user 消息。")
                                },
                                colors = rememberSettingsOutlineColors(),
                            )
                        }
                    }
                }

                item {
                    SettingsGroup(title = "连通性测试") {
                        SettingsListRow(
                            title = "当前测试模型",
                            supportingText = "$defaultModelId · ${resolveMimoChatCompletionsEndpoint(normalizedSettings.baseUrl)}",
                            showArrow = false,
                        )
                        SettingsGroupDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "会用当前页面里的 Key、模型和音色配置合成一句测试语音，只验证接口，不保存音频。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            NarraButton(
                                onClick = { onTestSettings(normalizedSettings) },
                                enabled = !isTesting && normalizedSettings.apiKey.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.accentStrong,
                                    contentColor = palette.accentOnStrong,
                                ),
                            ) {
                                Text(if (isTesting) "测试中..." else "测试 MiMo")
                            }
                        }
                    }
                }

                if (!uiMessage.isNullOrBlank()) {
                    item {
                        SettingsNoticeCard(
                            title = if (uiMessage.contains("失败")) "测试结果" else "状态",
                            body = uiMessage,
                            containerColor = if (uiMessage.contains("失败")) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                palette.accentSoft
                            },
                            contentColor = if (uiMessage.contains("失败")) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                palette.title
                            },
                        )
                    }
                }

                item {
                    SettingsSectionHeader(
                        title = "角色音色",
                        description = "每个角色可以继承全局音色，也可以单独使用预置音色或文本设计音色。",
                    )
                }

                items(
                    items = assistants,
                    key = Assistant::id,
                ) { assistant ->
                    val profile = normalizedSettings.assistantProfiles[assistant.id]
                    SettingsGroup {
                        SettingsListRow(
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = palette.title,
                                )
                            },
                            title = assistant.name.ifBlank { "未命名角色" },
                            supportingText = profile?.storageLabel() ?: "继承全局",
                            onClick = { editingAssistantId = assistant.id },
                        )
                    }
                }
            }
        }
    }

    if (showDefaultVoiceSheet) {
        PresetVoiceSheet(
            selectedVoiceId = defaultProfile.presetVoiceId,
            onDismissRequest = { showDefaultVoiceSheet = false },
            onSelectVoice = { voiceId ->
                onUpdateSettings(
                    normalizedSettings.copy(
                        defaultProfile = defaultProfile.copy(
                            mode = VoiceProfileMode.PRESET,
                            presetVoiceId = voiceId,
                        ),
                    ),
                )
                showDefaultVoiceSheet = false
            },
        )
    }

    if (showDefaultModelSheet) {
        DefaultVoiceModelSheet(
            selectedMode = defaultProfile.mode,
            onDismissRequest = { showDefaultModelSheet = false },
            onSelectMode = { mode ->
                onUpdateSettings(
                    normalizedSettings.copy(
                        defaultProfile = defaultProfile.copy(mode = mode).normalized(),
                    ),
                )
                showDefaultModelSheet = false
            },
        )
    }

    if (selectedAssistant != null) {
        AssistantVoiceProfileSheet(
            assistant = selectedAssistant,
            initialProfile = normalizedSettings.assistantProfiles[selectedAssistant.id]
                ?: VoiceProfile(),
            isTesting = isTesting,
            onDismissRequest = { editingAssistantId = null },
            onTestProfile = { profile ->
                onTestSettings(
                    normalizedSettings.copy(
                        defaultProfile = profile.normalized(),
                    ),
                )
            },
            onSaveProfile = { profile ->
                val updatedProfiles = if (profile.mode == VoiceProfileMode.INHERIT) {
                    normalizedSettings.assistantProfiles - selectedAssistant.id
                } else {
                    normalizedSettings.assistantProfiles + (selectedAssistant.id to profile.normalized())
                }
                onUpdateSettings(
                    normalizedSettings.copy(
                        assistantProfiles = updatedProfiles,
                    ),
                )
                editingAssistantId = null
            },
        )
    }
}

@Composable
private fun VoiceSwitchRow(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetVoiceSheet(
    selectedVoiceId: String,
    onDismissRequest: () -> Unit,
    onSelectVoice: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "选择预置音色",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            MIMO_PRESET_VOICES.forEach { voice ->
                SettingsListRow(
                    title = voice.displayName,
                    supportingText = voice.language,
                    highlighted = voice.id == selectedVoiceId,
                    onClick = { onSelectVoice(voice.id) },
                    showArrow = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultVoiceModelSheet(
    selectedMode: VoiceProfileMode,
    onDismissRequest: () -> Unit,
    onSelectMode: (VoiceProfileMode) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "选择默认合成模型",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            SettingsListRow(
                title = MIMO_TTS_MODEL_PRESET,
                supportingText = "使用冰糖、茉莉、苏打、白桦、Mia 等预置音色",
                highlighted = selectedMode != VoiceProfileMode.VOICE_DESIGN,
                onClick = { onSelectMode(VoiceProfileMode.PRESET) },
                showArrow = false,
            )
            SettingsListRow(
                title = MIMO_TTS_MODEL_VOICE_DESIGN,
                supportingText = "用一段文本描述设计音色，测试时会校验描述是否可用",
                highlighted = selectedMode == VoiceProfileMode.VOICE_DESIGN,
                onClick = { onSelectMode(VoiceProfileMode.VOICE_DESIGN) },
                showArrow = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantVoiceProfileSheet(
    assistant: Assistant,
    initialProfile: VoiceProfile,
    isTesting: Boolean,
    onDismissRequest: () -> Unit,
    onTestProfile: (VoiceProfile) -> Unit,
    onSaveProfile: (VoiceProfile) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var mode by rememberSaveable(assistant.id) { mutableStateOf(initialProfile.mode) }
    var presetVoiceId by rememberSaveable(assistant.id) {
        mutableStateOf(initialProfile.presetVoiceId.ifBlank { MIMO_DEFAULT_VOICE_ID })
    }
    var voiceDesignPrompt by rememberSaveable(assistant.id) {
        mutableStateOf(initialProfile.voiceDesignPrompt)
    }
    var voiceCloneSamplePath by rememberSaveable(assistant.id) {
        mutableStateOf(initialProfile.voiceCloneSamplePath)
    }
    var voiceCloneSampleMimeType by rememberSaveable(assistant.id) {
        mutableStateOf(initialProfile.voiceCloneSampleMimeType)
    }
    var voiceCloneSampleFileName by rememberSaveable(assistant.id) {
        mutableStateOf(initialProfile.voiceCloneSampleFileName)
    }
    var voiceCloneSampleSizeBytes by rememberSaveable(assistant.id) {
        mutableStateOf(initialProfile.voiceCloneSampleSizeBytes)
    }
    var voiceCloneImportError by rememberSaveable(assistant.id) { mutableStateOf("") }
    var showPresetVoiceSheet by rememberSaveable(assistant.id) { mutableStateOf(false) }
    val voiceCloneSampleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            voiceCloneImportError = ""
            runCatching {
                VoiceCloneSampleStorage.copyToAppStorage(
                    context = context,
                    uri = uri,
                    scope = "assistant-${assistant.id}",
                )
            }.onSuccess { sample ->
                val previousPath = voiceCloneSamplePath
                voiceCloneSamplePath = sample.path
                voiceCloneSampleMimeType = sample.mimeType
                voiceCloneSampleFileName = sample.fileName
                voiceCloneSampleSizeBytes = sample.sizeBytes
                if (previousPath.isNotBlank() && previousPath != sample.path) {
                    VoiceCloneSampleStorage.deleteIfLocal(context, previousPath)
                }
            }.onFailure { throwable ->
                voiceCloneImportError = throwable.message ?: "声音样本导入失败"
            }
        }
    }
    fun currentDraftProfile(): VoiceProfile {
        return VoiceProfile(
            mode = mode,
            presetVoiceId = presetVoiceId,
            voiceDesignPrompt = voiceDesignPrompt,
            voiceCloneSamplePath = voiceCloneSamplePath,
            voiceCloneSampleMimeType = voiceCloneSampleMimeType,
            voiceCloneSampleFileName = voiceCloneSampleFileName,
            voiceCloneSampleSizeBytes = voiceCloneSampleSizeBytes,
        )
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = assistant.name.ifBlank { "角色音色" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            SettingsGroup {
                VoiceProfileMode.entries.forEachIndexed { index, option ->
                    SettingsListRow(
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                            )
                        },
                        title = option.label,
                        supportingText = when (option) {
                            VoiceProfileMode.INHERIT -> "使用全局默认预置音色"
                            VoiceProfileMode.PRESET -> presetVoiceId
                            VoiceProfileMode.VOICE_DESIGN -> "用一句描述定制声音气质"
                            VoiceProfileMode.VOICE_CLONE -> voiceCloneSampleFileName.ifBlank { "导入 wav/mp3 样本复刻声音" }
                            VoiceProfileMode.DISABLED -> "这个角色不自动生成语音"
                        },
                        highlighted = mode == option,
                        onClick = { mode = option },
                        showArrow = false,
                    )
                    if (index < VoiceProfileMode.entries.lastIndex) {
                        SettingsGroupDivider()
                    }
                }
            }
            if (mode == VoiceProfileMode.PRESET) {
                SettingsGroup {
                    SettingsListRow(
                        title = "预置音色",
                        supportingText = presetVoiceId,
                        onClick = { showPresetVoiceSheet = true },
                    )
                }
            }
            if (mode == VoiceProfileMode.VOICE_CLONE) {
                SettingsNoticeCard(
                    title = "声音克隆",
                    body = "请选择你有权使用的 wav/mp3 样本。每次合成时，样本会随请求发送给当前 MiMo 或第三方中转接口；Base64 后不能超过 10 MB。",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                SettingsGroup {
                    SettingsListRow(
                        title = "样本文件",
                        supportingText = if (voiceCloneSamplePath.isBlank()) {
                            "尚未选择"
                        } else {
                            "${voiceCloneSampleFileName.ifBlank { "声音样本" }} · ${formatVoiceCloneSampleSize(voiceCloneSampleSizeBytes)}"
                        },
                        onClick = { voiceCloneSampleLauncher.launch(arrayOf("audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp3")) },
                    )
                    if (voiceCloneImportError.isNotBlank()) {
                        SettingsGroupDivider()
                        Text(
                            text = voiceCloneImportError,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    SettingsGroupDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NarraButton(
                            onClick = { onTestProfile(currentDraftProfile()) },
                            enabled = !isTesting && voiceCloneSamplePath.isNotBlank(),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(if (isTesting) "测试中..." else "测试克隆")
                        }
                        TextButton(
                            onClick = { voiceCloneSampleLauncher.launch(arrayOf("audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp3")) },
                        ) {
                            Text(if (voiceCloneSamplePath.isBlank()) "选择样本" else "更换样本")
                        }
                        TextButton(
                            enabled = voiceCloneSamplePath.isNotBlank(),
                            onClick = {
                                VoiceCloneSampleStorage.deleteIfLocal(context, voiceCloneSamplePath)
                                voiceCloneSamplePath = ""
                                voiceCloneSampleMimeType = ""
                                voiceCloneSampleFileName = ""
                                voiceCloneSampleSizeBytes = 0L
                                voiceCloneImportError = ""
                            },
                        ) {
                            Text("移除")
                        }
                    }
                }
            }
            if (mode == VoiceProfileMode.VOICE_DESIGN) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "音色设计描述",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            onClick = {
                                voiceDesignPrompt = buildVoiceDesignPromptFromAssistant(assistant)
                            },
                        ) {
                            Text("从人设生成")
                        }
                    }
                    OutlinedTextField(
                        value = voiceDesignPrompt,
                        onValueChange = { voiceDesignPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("VoiceDesign Prompt") },
                        minLines = 3,
                        supportingText = {
                            Text("会读取角色名称、简介、系统提示、场景、开场白和标签；生成后可以继续手动调整。")
                        },
                        colors = rememberSettingsOutlineColors(),
                    )
                    NarraButton(
                        onClick = { onTestProfile(currentDraftProfile()) },
                        enabled = !isTesting && voiceDesignPrompt.isNotBlank(),
                    ) {
                        Text(if (isTesting) "测试中..." else "测试音色")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text("取消")
                }
                TextButton(
                    onClick = {
                        onSaveProfile(currentDraftProfile())
                    },
                ) {
                    Text("保存")
                }
            }
        }
    }

    if (showPresetVoiceSheet) {
        PresetVoiceSheet(
            selectedVoiceId = presetVoiceId,
            onDismissRequest = { showPresetVoiceSheet = false },
            onSelectVoice = { voiceId ->
                presetVoiceId = voiceId
                showPresetVoiceSheet = false
            },
        )
    }
}

private fun formatVoiceCloneSampleSize(sizeBytes: Long): String {
    val safeBytes = sizeBytes.coerceAtLeast(0L)
    return if (safeBytes >= 1024 * 1024) {
        "%.1f MB".format(safeBytes / 1024f / 1024f)
    } else {
        "${(safeBytes / 1024f).coerceAtLeast(0.1f).let { "%.1f".format(it) }} KB"
    }
}
