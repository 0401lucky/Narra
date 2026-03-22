package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppUpdateAvailability
import com.example.myapplication.model.AppUpdateDownloadSnapshot
import com.example.myapplication.model.AppUpdateDownloadStatus
import com.example.myapplication.ui.component.AppSnackbarHost
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
    val primaryButtonLabel = when (uiState.downloadSnapshot.status) {
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
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        containerColor = palette.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                    overline = "安装包分发",
                    title = "启动自动检查，设置页手动更新",
                    summary = "当服务器返回更高版本时，可以在这里查看更新说明、下载 APK，并调用系统安装器完成升级。",
                )
            }

            item { SettingsSectionHeader("当前版本", "") }
            item {
                SettingsGroup {
                    SettingsListRow(
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = palette.title)
                        },
                        title = uiState.currentVersionName,
                        supportingText = "versionCode ${uiState.currentVersionCode} · ${uiState.channel}",
                        showArrow = false,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = {
                            Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, tint = palette.title)
                        },
                        title = if (uiState.hasConfiguredSource) "更新源已配置" else "更新源未配置",
                        supportingText = uiState.metadataBaseUrl.ifBlank { "请先在 Gradle 中配置 APP_UPDATE_METADATA_BASE_URL" },
                        showArrow = false,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = {
                            Icon(Icons.Default.Download, contentDescription = null, tint = palette.title)
                        },
                        title = if (uiState.lastCheckedAt > 0L) "最近检查时间" else "尚未检查更新",
                        supportingText = uiState.lastCheckedAt.takeIf { it > 0L }?.formatAsDateTime() ?: "点击下方按钮立即检查",
                        showArrow = false,
                    )
                }
            }

            item { SettingsSectionHeader("检查结果", "") }
            item {
                SettingsGroup {
                    SettingsListRow(
                        title = when (uiState.availability) {
                            AppUpdateAvailability.DISABLED -> "未启用更新"
                            AppUpdateAvailability.UNKNOWN -> if (uiState.isChecking) "检查中" else "等待检查"
                            AppUpdateAvailability.UP_TO_DATE -> "已是最新版本"
                            AppUpdateAvailability.OPTIONAL -> "发现可选更新"
                            AppUpdateAvailability.REQUIRED -> "发现强制更新"
                        },
                        supportingText = buildString {
                            val metadata = uiState.latestMetadata
                            if (metadata != null) {
                                append("服务器版本 ${metadata.latestVersionName} (${metadata.latestVersionCode})")
                            } else {
                                append("还没有拿到服务端版本信息")
                            }
                        },
                        showArrow = false,
                    )
                    uiState.downloadSnapshot.downloadId?.let { downloadId ->
                        SettingsGroupDivider()
                        SettingsListRow(
                            title = "下载任务 #$downloadId",
                            supportingText = uiState.downloadSnapshot.describeProgress(),
                            showArrow = false,
                        )
                    }
                }
            }

            if (uiState.availability == AppUpdateAvailability.REQUIRED) {
                item {
                    SettingsNoticeCard(
                        title = "当前版本已低于最低支持版本",
                        body = "这是一次强制更新。安装最新版本后才能继续正常使用应用。",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            uiState.latestMetadata?.takeIf { it.releaseNotes.isNotEmpty() }?.let { metadata ->
                item { SettingsSectionHeader("更新说明", "") }
                item {
                    SettingsGroup {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            metadata.releaseNotes.forEach { note ->
                                Text(
                                    text = "• $note",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.body,
                                )
                            }
                            if (metadata.publishedAt.isNotBlank()) {
                                Text(
                                    text = "发布时间：${metadata.publishedAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.body,
                                )
                            }
                        }
                    }
                }
            }

            item { SettingsSectionHeader("快捷操作", "") }
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
                )
            }
            item {
                AnimatedSettingButton(
                    text = "重新检查更新",
                    onClick = onCheckForUpdates,
                    enabled = !uiState.isChecking && uiState.hasConfiguredSource,
                    isPrimary = false,
                )
            }
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
