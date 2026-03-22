package com.example.myapplication.model

data class AppUpdateEnvironment(
    val appId: String,
    val channel: String,
    val versionName: String,
    val versionCode: Int,
    val metadataBaseUrl: String,
)

data class AppUpdateMetadata(
    val appId: String = "",
    val channel: String = "",
    val latestVersionName: String = "",
    val latestVersionCode: Int = 0,
    val minimumSupportedVersionCode: Int = 0,
    val apkUrl: String = "",
    val apkSha256: String = "",
    val publishedAt: String = "",
    val releaseNotes: List<String> = emptyList(),
)

enum class AppUpdateAvailability {
    UNKNOWN,
    DISABLED,
    UP_TO_DATE,
    OPTIONAL,
    REQUIRED,
}

data class AppUpdateCheckOutcome(
    val availability: AppUpdateAvailability = AppUpdateAvailability.UNKNOWN,
    val metadata: AppUpdateMetadata? = null,
    val checkedAt: Long = 0L,
    val errorMessage: String? = null,
    val fromCache: Boolean = false,
)

data class AppUpdateLocalState(
    val lastCheckAt: Long = 0L,
    val cachedMetadataJson: String = "",
    val activeDownloadId: Long = 0L,
)

enum class AppUpdateDownloadStatus {
    IDLE,
    PENDING,
    RUNNING,
    PAUSED,
    DOWNLOADED,
    FAILED,
    MISSING,
}

data class AppUpdateDownloadSnapshot(
    val status: AppUpdateDownloadStatus = AppUpdateDownloadStatus.IDLE,
    val downloadId: Long? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val reason: String? = null,
)

enum class AppUpdateInstallResultType {
    STARTED,
    REQUIRE_UNKNOWN_SOURCES_PERMISSION,
    HASH_MISMATCH,
    FILE_MISSING,
    ERROR,
}

data class AppUpdateInstallResult(
    val type: AppUpdateInstallResultType,
    val message: String? = null,
)

fun AppUpdateMetadata.hasUpdateFor(versionCode: Int): Boolean {
    return latestVersionCode > versionCode
}

fun AppUpdateMetadata.requiresImmediateUpdate(versionCode: Int): Boolean {
    return minimumSupportedVersionCode > versionCode
}

fun AppUpdateMetadata.normalizedSha256(): String {
    return apkSha256.trim().lowercase().replace(":", "").replace(" ", "")
}
