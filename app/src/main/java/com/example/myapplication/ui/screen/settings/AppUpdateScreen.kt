package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert
import com.example.myapplication.model.AppUpdateAvailability
import com.example.myapplication.model.AppUpdateDownloadSnapshot
import com.example.myapplication.model.AppUpdateDownloadStatus
import com.example.myapplication.ui.component.TopAppSnackbarHost
import com.example.myapplication.viewmodel.AppUpdateUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppUpdateScreen(
    uiState: AppUpdateUiState,
    onNavigateBack: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onStartDownload: () -> Unit,
    onInstallUpdate: () -> Unit,
    onConsumeMessage: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )
    val primaryButtonLabel = if (uiState.isChecking) "正在检查" else when (uiState.downloadSnapshot.status) {
        AppUpdateDownloadStatus.PENDING,
        AppUpdateDownloadStatus.RUNNING,
        AppUpdateDownloadStatus.PAUSED,
        -> "下载中"

        AppUpdateDownloadStatus.DOWNLOADED -> "安装更新"
        else -> if (uiState.hasAvailableUpdate) "立即更新" else "检查更新"
    }
    val primaryButtonEnabled = !uiState.isChecking && when (uiState.downloadSnapshot.status) {
        AppUpdateDownloadStatus.PENDING,
        AppUpdateDownloadStatus.RUNNING,
        AppUpdateDownloadStatus.PAUSED,
        -> false

        else -> true
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "版本与更新",
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
                    Column(
                        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "当前版本",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.body,
                        )
                        Text(
                            text = "Narra ${uiState.currentVersionName}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.title,
                        )
                        Text(
                            text = if (uiState.lastCheckedAt > 0L) {
                                "最近检查 ${uiState.lastCheckedAt.formatAsDateTime()}"
                            } else {
                                "尚未检查更新"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.body,
                        )
                    }
                }
                item { HorizontalDivider(color = palette.border) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = when {
                                    !uiState.hasConfiguredSource -> Lucide.TriangleAlert
                                    uiState.availability == AppUpdateAvailability.UP_TO_DATE -> Lucide.Check
                                    uiState.hasAvailableUpdate -> Lucide.Download
                                    else -> Lucide.RefreshCw
                                },
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = palette.accent,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = if (uiState.isChecking) "正在检查更新" else when (uiState.availability) {
                                        AppUpdateAvailability.DISABLED -> "此版本暂不支持在线更新"
                                        AppUpdateAvailability.UNKNOWN -> "等待检查"
                                        AppUpdateAvailability.UP_TO_DATE -> "已是最新版本"
                                        AppUpdateAvailability.OPTIONAL,
                                        AppUpdateAvailability.REQUIRED,
                                        -> "发现可用更新"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.title,
                                )
                                uiState.latestMetadata?.let { metadata ->
                                    Text(
                                        text = "最新版本 ${metadata.latestVersionName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.body,
                                    )
                                }
                            }
                        }
                        if (uiState.isChecking) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        if (uiState.downloadSnapshot.status != AppUpdateDownloadStatus.IDLE) {
                            val download = uiState.downloadSnapshot
                            val failed = download.status == AppUpdateDownloadStatus.FAILED ||
                                download.status == AppUpdateDownloadStatus.MISSING
                            Text(
                                text = download.describeProgress(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (failed) MaterialTheme.colorScheme.error else palette.body,
                            )
                            if (download.status == AppUpdateDownloadStatus.RUNNING && download.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = {
                                        (download.bytesDownloaded.toFloat() / download.totalBytes).coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else if (download.status == AppUpdateDownloadStatus.PENDING ||
                                download.status == AppUpdateDownloadStatus.RUNNING
                            ) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                item {
                    AnimatedSettingButton(
                        text = primaryButtonLabel,
                        onClick = {
                            if (uiState.downloadSnapshot.status == AppUpdateDownloadStatus.DOWNLOADED) {
                                onInstallUpdate()
                            } else if (uiState.hasAvailableUpdate) {
                                onStartDownload()
                            } else {
                                onCheckForUpdates()
                            }
                        },
                        enabled = primaryButtonEnabled,
                        isPrimary = true,
                        leadingIcon = {
                            Icon(
                                imageVector = if (uiState.hasAvailableUpdate) Lucide.Download else Lucide.RefreshCw,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                }
                if (uiState.hasAvailableUpdate || uiState.downloadSnapshot.status != AppUpdateDownloadStatus.IDLE) {
                    item {
                        AnimatedSettingButton(
                            text = "重新检查更新",
                            onClick = onCheckForUpdates,
                            enabled = !uiState.isChecking && uiState.hasConfiguredSource,
                            isPrimary = false,
                        )
                    }
                }
                uiState.latestMetadata?.takeIf { it.releaseNotes.isNotEmpty() }?.let { metadata ->
                    item { HorizontalDivider(color = palette.border) }
                    item { SettingsSectionHeader("更新说明", "") }
                    items(metadata.releaseNotes.size) { index ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("•", color = palette.accent, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = metadata.releaseNotes[index],
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.title,
                            )
                        }
                    }
                    if (metadata.publishedAt.isNotBlank()) {
                        item {
                            Text(
                                text = "发布于 ${metadata.publishedAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.body,
                            )
                        }
                    }
                }
                item {
                    SettingsSectionHeader(
                        title = "更新信息",
                        description = buildString {
                            appendLine("版本编号：${uiState.currentVersionCode}")
                            appendLine("更新渠道：${uiState.channel}")
                            appendLine("更新源：${uiState.metadataBaseUrl.ifBlank { "未配置" }}")
                            uiState.latestMetadata?.let { metadata ->
                                appendLine("最新版本编号：${metadata.latestVersionCode}")
                            }
                            uiState.downloadSnapshot.downloadId?.let { downloadId ->
                                appendLine("下载任务：$downloadId")
                            }
                            append("应用启动时会自动检查更新。下载安装由你决定，安装时会打开系统安装器。")
                        },
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

private fun Long.formatAsDateTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this))
}

private fun AppUpdateDownloadSnapshot.describeProgress(): String {
    return when (status) {
        AppUpdateDownloadStatus.PENDING -> "已创建下载任务，等待系统开始下载"
        AppUpdateDownloadStatus.RUNNING -> {
            if (totalBytes > 0L) {
                val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                "正在下载，已完成 $progress%"
            } else {
                "正在下载更新包"
            }
        }

        AppUpdateDownloadStatus.PAUSED -> reason ?: "下载已暂停"
        AppUpdateDownloadStatus.DOWNLOADED -> "安装包已下载完成，可立即安装"
        AppUpdateDownloadStatus.FAILED -> reason ?: "下载失败"
        AppUpdateDownloadStatus.MISSING -> reason ?: "下载记录已丢失"
        AppUpdateDownloadStatus.IDLE -> "尚未开始下载"
    }
}
