package com.example.myapplication.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.VoiceRecognitionEngine
import com.example.myapplication.model.VoiceRecognitionSettings

/**
 * 语音识别设置页：总开关 + 引擎选择 + 「语音识别 API」二级页入口。
 *
 * 视觉与信息层级对齐 [VoiceSynthesisSettingsScreen]，两页并列出现在设置首页。
 */
@Composable
fun VoiceRecognitionSettingsScreen(
    settings: VoiceRecognitionSettings,
    systemRecognitionAvailable: Boolean,
    systemRecognitionProviders: List<String>,
    onUpdateSettings: (VoiceRecognitionSettings) -> Unit,
    onOpenApiSettings: () -> Unit,
    onSaveChanges: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val normalized = settings.normalized()

    BackHandler {
        onSaveChanges()
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "语音识别",
                subtitle = "按住说话，转成文字",
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
                    SettingsGroup(title = "开关") {
                        VoiceRecognitionSwitchRow(
                            title = "启用语音识别",
                            supportingText = if (normalized.enabled) {
                                "输入框会出现麦克风按钮，按住说话"
                            } else {
                                "关闭时输入框不显示麦克风按钮"
                            },
                            checked = normalized.enabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings(normalized.copy(enabled = enabled))
                            },
                        )
                    }
                }

                item {
                    SettingsGroup(
                        title = "识别通道",
                        subtitle = "系统识别免配置且不上传音频，但是否可用取决于设备服务和录音权限；" +
                            "大模型识别需要联网和 API Key，松手后会把录音上传到你配置的服务。",
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                VoiceRecognitionEngine.entries.forEachIndexed { index, engine ->
                                    SegmentedButton(
                                        selected = normalized.engine == engine,
                                        onClick = {
                                            onUpdateSettings(normalized.copy(engine = engine))
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = VoiceRecognitionEngine.entries.size,
                                        ),
                                    ) {
                                        Text(engine.label)
                                    }
                                }
                            }
                            Text(
                                text = engineDescription(normalized.engine),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.body,
                            )
                        }
                        if (normalized.engine == VoiceRecognitionEngine.SYSTEM) {
                            SettingsGroupDivider()
                            SettingsListRow(
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = palette.title,
                                    )
                                },
                                title = "这台设备的系统识别",
                                supportingText = systemRecognitionSummary(
                                    available = systemRecognitionAvailable,
                                    providers = systemRecognitionProviders,
                                ),
                            )
                        }
                    }
                }

                item {
                    SettingsGroup(title = "大模型识别配置") {
                        SettingsListRow(
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = palette.title,
                                )
                            },
                            title = "语音识别 API",
                            supportingText = apiSummary(normalized),
                            onClick = onOpenApiSettings,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 把系统识别的可用性讲清楚。可用只代表「有服务声明」，不代表该服务愿意为三方应用录音——
 * 部分 ROM 会在已授权麦克风的情况下仍然返回权限不足，所以这里把提供方包名一并显示。
 */
private fun systemRecognitionSummary(
    available: Boolean,
    providers: List<String>,
): String {
    if (!available) {
        return "不可用：没有找到系统识别服务，建议改用大模型识别"
    }
    if (providers.isEmpty()) {
        return "已声明可用，但查不到提供方；若实际无法录音请改用大模型识别"
    }
    return "可用 · 提供方：${providers.joinToString("、")}"
}

private fun engineDescription(engine: VoiceRecognitionEngine): String {
    return when (engine) {
        VoiceRecognitionEngine.SYSTEM ->
            "由系统识别服务处理，音频不经过本应用上传；支持边说边出中间结果。"

        VoiceRecognitionEngine.REMOTE_MODEL ->
            "松手后把录音上传到你配置的转写接口（OpenAI 兼容 /audio/transcriptions）。"
    }
}

private fun apiSummary(settings: VoiceRecognitionSettings): String {
    if (!settings.hasUsableCredentialForRemote()) {
        return "未配置：填写 Base URL 与 API Key 后才能使用大模型识别"
    }
    val language = settings.language.ifBlank { "自动" }
    return "已配置 · ${settings.protocol.label} · ${settings.model} · $language"
}

/** 大模型通道的凭据是否齐备，与当前选中的引擎无关。 */
private fun VoiceRecognitionSettings.hasUsableCredentialForRemote(): Boolean {
    return apiKey.trim().isNotBlank() && baseUrl.trim().isNotBlank()
}

@Composable
private fun VoiceRecognitionSwitchRow(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
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
