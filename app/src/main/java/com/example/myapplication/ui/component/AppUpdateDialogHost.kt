package com.example.myapplication.ui.component

import com.example.myapplication.ui.component.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.model.AppUpdateDownloadStatus
import com.example.myapplication.viewmodel.AppUpdateUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppUpdateDialogHost(
    uiState: AppUpdateUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    if (!uiState.isDialogVisible || !uiState.hasAvailableUpdate) {
        return
    }

    val metadata = uiState.latestMetadata ?: return
    val primaryLabel = when (uiState.downloadSnapshot.status) {
        AppUpdateDownloadStatus.PENDING,
        AppUpdateDownloadStatus.RUNNING,
        AppUpdateDownloadStatus.PAUSED,
        -> "下载中"

        AppUpdateDownloadStatus.DOWNLOADED -> "安装更新"
        else -> "立即更新"
    }
    val primaryEnabled = when (uiState.downloadSnapshot.status) {
        AppUpdateDownloadStatus.PENDING,
        AppUpdateDownloadStatus.RUNNING,
        AppUpdateDownloadStatus.PAUSED,
        -> false

        else -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        title = {
            Text(
                text = "发现新版本",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("当前版本：${uiState.currentVersionName} (${uiState.currentVersionCode})")
                Text("最新版本：${metadata.latestVersionName} (${metadata.latestVersionCode})")
                if (metadata.publishedAt.isNotBlank()) {
                    Text("发布时间：${metadata.publishedAt}")
                }
                uiState.downloadSnapshot.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                    Text("下载状态：$reason")
                }
                if (metadata.releaseNotes.isNotEmpty()) {
                    Text("更新说明")
                    metadata.releaseNotes.forEach { note ->
                        Text("• $note")
                    }
                }
                if (uiState.lastCheckedAt > 0L) {
                    Text("最近检查：${uiState.lastCheckedAt.formatAsDateTime()}")
                }
            }
        },
        confirmButton = {
            NarraTextButton(
                onClick = {
                    if (uiState.downloadSnapshot.status == AppUpdateDownloadStatus.DOWNLOADED) {
                        onInstall()
                    } else {
                        onDownload()
                    }
                },
                enabled = primaryEnabled,
            ) {
                Text(primaryLabel)
            }
        },
        dismissButton = {
            NarraTextButton(onClick = onDismiss) {
                Text("稍后再说")
            }
        },
    )
}

private fun Long.formatAsDateTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this))
}
