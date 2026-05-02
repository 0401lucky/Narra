package com.example.myapplication.system.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.example.myapplication.model.AppUpdateDownloadSnapshot
import com.example.myapplication.model.AppUpdateDownloadStatus
import com.example.myapplication.model.AppUpdateInstallResult
import com.example.myapplication.model.AppUpdateInstallResultType
import com.example.myapplication.model.AppUpdateMetadata
import com.example.myapplication.model.normalizedSha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

interface AppUpdateDownloadController {
    fun enqueueDownload(metadata: AppUpdateMetadata): Long

    fun queryDownload(downloadId: Long): AppUpdateDownloadSnapshot

    suspend fun installDownloadedPackage(
        downloadId: Long,
        metadata: AppUpdateMetadata,
    ): AppUpdateInstallResult
}

class AndroidAppUpdateController(
    private val context: Context,
) : AppUpdateDownloadController {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)

    override fun enqueueDownload(metadata: AppUpdateMetadata): Long {
        val request = DownloadManager.Request(metadata.apkUrl.toUri()).apply {
            setTitle(buildDownloadTitle(metadata))
            setDescription("下载最新安装包")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        return downloadManager.enqueue(request)
    }

    override fun queryDownload(downloadId: Long): AppUpdateDownloadSnapshot {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                return AppUpdateDownloadSnapshot(
                    status = AppUpdateDownloadStatus.MISSING,
                    downloadId = downloadId,
                    reason = "下载任务不存在",
                )
            }

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val reasonCode = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))

            return AppUpdateDownloadSnapshot(
                status = status.toDownloadStatus(),
                downloadId = downloadId,
                bytesDownloaded = bytesDownloaded.coerceAtLeast(0L),
                totalBytes = totalBytes.coerceAtLeast(0L),
                reason = reasonCode.toDownloadReason(status),
            )
        }
    }

    override suspend fun installDownloadedPackage(
        downloadId: Long,
        metadata: AppUpdateMetadata,
    ): AppUpdateInstallResult = withContext(Dispatchers.IO) {
        val snapshot = queryDownload(downloadId)
        if (snapshot.status != AppUpdateDownloadStatus.DOWNLOADED) {
            return@withContext AppUpdateInstallResult(
                type = AppUpdateInstallResultType.FILE_MISSING,
                message = snapshot.reason ?: "更新包尚未下载完成",
            )
        }

        val uri = downloadManager.getUriForDownloadedFile(downloadId)
            ?: return@withContext AppUpdateInstallResult(
                type = AppUpdateInstallResultType.FILE_MISSING,
                message = "找不到已下载的安装包",
            )

        val expectedSha256 = metadata.normalizedSha256()
        if (expectedSha256.isBlank()) {
            return@withContext AppUpdateInstallResult(
                type = AppUpdateInstallResultType.ERROR,
                message = "安装包缺少校验信息，请重新检查更新",
            )
        }
        val actualSha256 = computeSha256(uri)
        if (actualSha256.isBlank() || actualSha256 != expectedSha256) {
            return@withContext AppUpdateInstallResult(
                type = AppUpdateInstallResultType.HASH_MISMATCH,
                message = "安装包校验失败，请重新下载",
            )
        }

        val canRequestPackageInstalls = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            appContext.packageManager.canRequestPackageInstalls()
        if (!canRequestPackageInstalls) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${appContext.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(permissionIntent)
            return@withContext AppUpdateInstallResult(
                type = AppUpdateInstallResultType.REQUIRE_UNKNOWN_SOURCES_PERMISSION,
                message = "请先允许安装未知来源应用",
            )
        }

        return@withContext runCatching {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            appContext.startActivity(installIntent)
            AppUpdateInstallResult(
                type = AppUpdateInstallResultType.STARTED,
                message = "已打开系统安装器",
            )
        }.getOrElse { throwable ->
            AppUpdateInstallResult(
                type = AppUpdateInstallResultType.ERROR,
                message = throwable.message ?: "无法启动系统安装器",
            )
        }
    }

    private fun computeSha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val readSize = inputStream.read(buffer)
                if (readSize <= 0) {
                    break
                }
                digest.update(buffer, 0, readSize)
            }
        } ?: return ""
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private fun buildDownloadTitle(metadata: AppUpdateMetadata): String {
        val versionLabel = metadata.latestVersionName.ifBlank { metadata.latestVersionCode.toString() }
        return "Narra $versionLabel"
    }
}

private fun Int.toDownloadStatus(): AppUpdateDownloadStatus {
    return when (this) {
        DownloadManager.STATUS_PENDING -> AppUpdateDownloadStatus.PENDING
        DownloadManager.STATUS_RUNNING -> AppUpdateDownloadStatus.RUNNING
        DownloadManager.STATUS_PAUSED -> AppUpdateDownloadStatus.PAUSED
        DownloadManager.STATUS_SUCCESSFUL -> AppUpdateDownloadStatus.DOWNLOADED
        DownloadManager.STATUS_FAILED -> AppUpdateDownloadStatus.FAILED
        else -> AppUpdateDownloadStatus.IDLE
    }
}

private fun Int.toDownloadReason(status: Int): String? {
    return when (status) {
        DownloadManager.STATUS_PAUSED -> when (this) {
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "等待网络恢复"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "等待系统自动重试"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "等待 Wi-Fi 下载"
            else -> "下载已暂停"
        }

        DownloadManager.STATUS_FAILED -> when (this) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "找不到可用存储设备"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "下载文件已存在"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "下载数据异常"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "下载地址重定向过多"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "服务器返回了不支持的状态码"
            DownloadManager.ERROR_UNKNOWN -> "下载失败"
            else -> "下载失败"
        }

        else -> null
    }
}
