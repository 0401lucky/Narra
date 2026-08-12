package com.example.myapplication.ui.screen.diagnostics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.myapplication.system.diagnostics.HistoricalExitQuerySupport
import com.example.myapplication.ui.component.TopAppSnackbarHost
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsGroup
import com.example.myapplication.ui.screen.settings.SettingsGroupDivider
import com.example.myapplication.ui.screen.settings.SettingsHintCard
import com.example.myapplication.ui.screen.settings.SettingsListRow
import com.example.myapplication.ui.screen.settings.SettingsPageIntro
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsSectionHeader
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette
import com.example.myapplication.ui.screen.settings.rememberSettingsSnackbarHostState
import com.example.myapplication.viewmodel.DiagnosticsUiState
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticsScreenHost(
    uiState: DiagnosticsUiState,
    onExportDocumentSelected: ((() -> OutputStream?)?) -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        onExportDocumentSelected(
            uri?.let { targetUri ->
                { context.contentResolver.openOutputStream(targetUri, "w") }
            },
        )
    }
    DiagnosticsScreen(
        uiState = uiState,
        onExport = { exportLauncher.launch(DIAGNOSTICS_EXPORT_FILE_NAME) },
        onConsumeMessage = onConsumeMessage,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
fun DiagnosticsScreen(
    uiState: DiagnosticsUiState,
    onExport: () -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )
    val latestExitText = remember(uiState.latestExitTimestampEpochMillis) {
        uiState.latestExitTimestampEpochMillis?.let(::formatExitTimestamp) ?: "暂无异常退出记录"
    }
    val supportText = when (uiState.supportStatus) {
        HistoricalExitQuerySupport.SUPPORTED -> "当前系统支持历史退出查询"
        HistoricalExitQuerySupport.UNSUPPORTED -> "Android 11 以下不支持历史退出查询"
    }

    Scaffold(
        modifier = Modifier.testTag(DIAGNOSTICS_SCREEN_TAG),
        topBar = {
            SettingsTopBar(
                title = "本地诊断",
                onNavigateBack = onNavigateBack,
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SettingsPageIntro(
                        title = "只在设备本地保留有限退出摘要",
                        summary = "Narra 不会自动上传或分享诊断信息。只有你主动选择保存位置后，才会生成 JSON 文件。",
                    )
                }
                item { SettingsSectionHeader("诊断状态", "") }
                item {
                    SettingsGroup {
                        SettingsListRow(
                            title = "系统支持",
                            supportingText = supportText,
                            enabled = false,
                            showArrow = false,
                        )
                        SettingsGroupDivider()
                        SettingsListRow(
                            title = "本地记录",
                            supportingText = if (uiState.isLoading) {
                                "正在读取…"
                            } else {
                                "共 ${uiState.recordCount} 条，最多保留 20 条"
                            },
                            modifier = Modifier.testTag(DIAGNOSTICS_RECORD_COUNT_TAG),
                            enabled = false,
                            showArrow = false,
                        )
                        SettingsGroupDivider()
                        SettingsListRow(
                            title = "最近异常退出",
                            supportingText = latestExitText,
                            enabled = false,
                            showArrow = false,
                        )
                    }
                }
                item {
                    SettingsHintCard(
                        title = "隐私边界",
                        body = "导出仅含异常类别、时间、应用版本、PSS/RSS 与安全数值；不含聊天内容、提示词、API Key、日志、系统跟踪文件或设备唯一标识。",
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AnimatedSettingButton(
                            text = if (uiState.isExporting) "正在导出…" else "导出诊断信息",
                            onClick = onExport,
                            enabled = !uiState.isLoading && !uiState.isExporting,
                            isPrimary = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                )
                            },
                        )
                        Text(
                            text = "导出失败不会删除现有本地记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.body,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                item {
                    SettingsGroup {
                        SettingsListRow(
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.HealthAndSafety,
                                    contentDescription = null,
                                    tint = palette.accent,
                                )
                            },
                            title = "默认离线",
                            supportingText = "不申请额外权限，不接入远程崩溃 SDK，也不安装全局未捕获异常上报器。",
                            enabled = false,
                            showArrow = false,
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

private fun formatExitTimestamp(epochMillis: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(epochMillis))
}

const val DIAGNOSTICS_SCREEN_TAG = "diagnostics_screen"
const val DIAGNOSTICS_RECORD_COUNT_TAG = "diagnostics_record_count"
private const val DIAGNOSTICS_EXPORT_FILE_NAME = "Narra-local-diagnostics.json"
