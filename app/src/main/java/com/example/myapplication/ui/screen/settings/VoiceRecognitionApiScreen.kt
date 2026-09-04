package com.example.myapplication.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.DEFAULT_TRANSCRIPTION_MODEL
import com.example.myapplication.model.VoiceRecognitionSettings
import com.example.myapplication.model.resolveTranscriptionEndpoint
import com.example.myapplication.ui.component.NarraButton

/**
 * 语音识别 API 配置页：Base URL / API Key / 模型 / 语言 + 连通性测试。
 */
@Composable
fun VoiceRecognitionApiScreen(
    settings: VoiceRecognitionSettings,
    isTesting: Boolean,
    onUpdateSettings: (VoiceRecognitionSettings) -> Unit,
    onTestConnection: (VoiceRecognitionSettings) -> Unit,
    onSaveChanges: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val normalized = settings.normalized()
    var showApiKey by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        onSaveChanges()
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "语音识别 API",
                subtitle = "OpenAI 兼容的转写接口",
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
                .padding(innerPadding)
                .imePadding(),
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
                        title = "请求去哪里",
                        body = "只需填到 /v1，应用会自动请求 /audio/transcriptions；" +
                            "已经带上这段路径的地址不会被重复拼接。",
                        containerColor = palette.accent,
                        contentColor = palette.accent,
                    )
                }

                item {
                    SettingsGroup(title = "接口地址与凭据") {
                        OutlinedTextField(
                            value = normalized.baseUrl,
                            onValueChange = { baseUrl ->
                                onUpdateSettings(normalized.copy(baseUrl = baseUrl))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            label = { Text("Base URL") },
                            singleLine = true,
                            supportingText = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("必须使用 https://，本机调试地址除外。")
                                    Text(endpointPreview(normalized.baseUrl))
                                }
                            },
                            colors = rememberSettingsOutlineColors(),
                        )
                        SettingsGroupDivider()
                        OutlinedTextField(
                            value = normalized.apiKey,
                            onValueChange = { apiKey ->
                                onUpdateSettings(normalized.copy(apiKey = apiKey))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            label = { Text("API Key") },
                            singleLine = true,
                            visualTransformation = if (showApiKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            supportingText = {
                                Text(
                                    text = if (normalized.apiKey.isBlank()) {
                                        "未填写，大模型识别不可用"
                                    } else {
                                        "当前已填写 ${normalized.apiKey.length} 位，可点右侧图标核对"
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
                    }
                }

                item {
                    SettingsGroup(title = "转写参数") {
                        OutlinedTextField(
                            value = normalized.model,
                            onValueChange = { model ->
                                onUpdateSettings(normalized.copy(model = model))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            label = { Text("模型") },
                            singleLine = true,
                            supportingText = {
                                Text("留空则使用 $DEFAULT_TRANSCRIPTION_MODEL。具体可用模型取决于你的服务商。")
                            },
                            colors = rememberSettingsOutlineColors(),
                        )
                        SettingsGroupDivider()
                        OutlinedTextField(
                            value = normalized.language,
                            onValueChange = { language ->
                                onUpdateSettings(normalized.copy(language = language))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            label = { Text("语言") },
                            singleLine = true,
                            supportingText = {
                                Text("如 zh、en。留空则不发送该字段，由服务端自动判断。")
                            },
                            colors = rememberSettingsOutlineColors(),
                        )
                    }
                }

                item {
                    SettingsGroup(title = "连通性测试") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            NarraButton(
                                onClick = { onTestConnection(normalized) },
                                enabled = !isTesting,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(end = 8.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                                Text(if (isTesting) "测试中…" else "测试连接")
                            }
                            Text(
                                text = "测试只探测端点与鉴权，不会真的上传录音，" +
                                    "因此不能验证模型名是否可用——模型是否正确要等第一次实际识别才知道。",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.body,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun endpointPreview(baseUrl: String): String {
    if (baseUrl.isBlank()) {
        return "当前请求：未填写"
    }
    return runCatching { "当前请求：${resolveTranscriptionEndpoint(baseUrl)}" }
        .getOrElse { throwable ->
            throwable.message?.takeIf { it.isNotBlank() } ?: "地址无效"
        }
}
