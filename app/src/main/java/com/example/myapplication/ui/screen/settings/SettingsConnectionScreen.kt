package com.example.myapplication.ui.screen.settings

import com.example.myapplication.ui.component.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.example.myapplication.ui.component.AppSnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.viewmodel.SettingsUiState

@Composable
fun SettingsConnectionScreen(
    uiState: SettingsUiState,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    val outlineColors = rememberSettingsOutlineColors()

    Scaffold(
        topBar = { SettingsTopBar(title = "连接与凭据", onNavigateBack = onNavigateBack) },
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
                    top = 12.dp,
                    end = SettingsScreenPadding,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    SettingsPageIntro(
                        title = "API 连接配置",
                    )
                }
                item {
                    SettingsSectionHeader("连接信息", "")
                }
                item {
                    SettingsGroup {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = uiState.baseUrl,
                                onValueChange = onBaseUrlChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Base URL") },
                                placeholder = { Text("https://api.openai.com/v1/") },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                colors = outlineColors,
                            )
                            OutlinedTextField(
                                value = uiState.apiKey,
                                onValueChange = onApiKeyChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("API Key") },
                                singleLine = true,
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                shape = RoundedCornerShape(18.dp),
                                colors = outlineColors,
                                trailingIcon = {
                                    NarraTextButton(onClick = { showApiKey = !showApiKey }) {
                                        Text(if (showApiKey) "隐藏" else "显示")
                                    }
                                },
                            )
                        }
                    }
                }
                if (uiState.baseUrl.isBlank() || uiState.apiKey.isBlank()) {
                    item {
                        SettingsNoticeCard(
                            title = "连接信息还不完整",
                            body = "Base URL 和 API Key 都需要填写，之后才能同步模型列表。",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
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
