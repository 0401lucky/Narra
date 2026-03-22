package com.example.myapplication.data.repository

import com.example.myapplication.data.local.AppUpdateStateStore
import com.example.myapplication.model.AppUpdateAvailability
import com.example.myapplication.model.AppUpdateCheckOutcome
import com.example.myapplication.model.AppUpdateEnvironment
import com.example.myapplication.model.AppUpdateLocalState
import com.example.myapplication.model.AppUpdateMetadata
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.UnknownHostException

private const val AutoCheckIntervalMs = 24L * 60L * 60L * 1000L

class AppUpdateRepository(
    private val stateStore: AppUpdateStateStore,
    private val metadataFetcher: suspend (String) -> String = { url -> fetchMetadata(url) },
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    val localStateFlow: Flow<AppUpdateLocalState> = stateStore.stateFlow

    suspend fun checkForUpdates(
        environment: AppUpdateEnvironment,
        manual: Boolean,
    ): AppUpdateCheckOutcome? {
        if (environment.metadataBaseUrl.isBlank()) {
            return AppUpdateCheckOutcome(
                availability = AppUpdateAvailability.DISABLED,
                checkedAt = stateStore.stateFlow.first().lastCheckAt,
            )
        }

        val localState = stateStore.stateFlow.first()
        val now = nowProvider()
        if (!manual && now - localState.lastCheckAt < AutoCheckIntervalMs) {
            return evaluateCachedOutcome(environment)
        }

        return try {
            val rawJson = metadataFetcher(buildMetadataUrl(environment))
            val metadata = parseMetadata(rawJson)
            val outcome = evaluateMetadata(
                metadata = metadata,
                environment = environment,
                checkedAt = now,
                fromCache = false,
            )
            stateStore.saveLastCheckAt(now)
            if (outcome.availability == AppUpdateAvailability.OPTIONAL ||
                outcome.availability == AppUpdateAvailability.REQUIRED
            ) {
                stateStore.saveCachedMetadataJson(rawJson)
            } else {
                stateStore.clearCachedMetadata()
            }
            outcome
        } catch (throwable: Throwable) {
            val cachedRequiredOutcome = evaluateCachedOutcome(environment)
                ?.takeIf { it.availability == AppUpdateAvailability.REQUIRED }
            cachedRequiredOutcome?.copy(
                errorMessage = throwable.toUserFacingMessage(),
                fromCache = true,
            ) ?: AppUpdateCheckOutcome(
                availability = AppUpdateAvailability.UNKNOWN,
                checkedAt = localState.lastCheckAt,
                errorMessage = throwable.toUserFacingMessage(),
            )
        }
    }

    suspend fun evaluateCachedOutcome(
        environment: AppUpdateEnvironment,
    ): AppUpdateCheckOutcome? {
        if (environment.metadataBaseUrl.isBlank()) {
            return null
        }

        val localState = stateStore.stateFlow.first()
        val rawJson = localState.cachedMetadataJson.trim()
        if (rawJson.isBlank()) {
            return null
        }

        val metadata = runCatching { parseMetadata(rawJson) }.getOrNull() ?: return null
        return runCatching {
            evaluateMetadata(
                metadata = metadata,
                environment = environment,
                checkedAt = localState.lastCheckAt,
                fromCache = true,
            )
        }.getOrNull()
    }

    suspend fun saveActiveDownloadId(downloadId: Long) {
        stateStore.saveActiveDownloadId(downloadId)
    }

    suspend fun clearActiveDownloadId() {
        stateStore.clearActiveDownloadId()
    }

    suspend fun clearCachedMetadata() {
        stateStore.clearCachedMetadata()
    }

    internal fun parseMetadata(rawJson: String): AppUpdateMetadata {
        val parsed = gson.fromJson(rawJson, AppUpdateMetadata::class.java)
            ?: throw IllegalStateException("更新元数据为空")
        return parsed.copy(
            appId = parsed.appId.trim(),
            channel = parsed.channel.trim(),
            latestVersionName = parsed.latestVersionName.trim(),
            minimumSupportedVersionCode = parsed.minimumSupportedVersionCode.coerceAtLeast(0),
            apkUrl = parsed.apkUrl.trim(),
            apkSha256 = parsed.apkSha256.trim(),
            publishedAt = parsed.publishedAt.trim(),
            releaseNotes = parsed.releaseNotes.mapNotNull { note ->
                note.trim().takeIf { it.isNotEmpty() }
            },
        )
    }

    internal fun evaluateMetadata(
        metadata: AppUpdateMetadata,
        environment: AppUpdateEnvironment,
        checkedAt: Long,
        fromCache: Boolean,
    ): AppUpdateCheckOutcome {
        require(metadata.appId == environment.appId) { "更新元数据中的应用标识不匹配" }
        require(metadata.channel == environment.channel) { "更新元数据中的渠道不匹配" }
        require(metadata.latestVersionCode > 0) { "更新元数据缺少有效的版本号" }
        if (metadata.latestVersionCode > environment.versionCode) {
            require(metadata.apkUrl.isNotBlank()) { "更新元数据缺少 APK 下载地址" }
        }

        val availability = when {
            metadata.latestVersionCode <= environment.versionCode -> AppUpdateAvailability.UP_TO_DATE
            metadata.minimumSupportedVersionCode > environment.versionCode -> AppUpdateAvailability.REQUIRED
            else -> AppUpdateAvailability.OPTIONAL
        }
        return AppUpdateCheckOutcome(
            availability = availability,
            metadata = metadata,
            checkedAt = checkedAt,
            fromCache = fromCache,
        )
    }

    private fun buildMetadataUrl(environment: AppUpdateEnvironment): String {
        val normalizedBaseUrl = environment.metadataBaseUrl.trim().removeSuffix("/")
        return "$normalizedBaseUrl/${environment.channel}.json"
    }

    private companion object {
        private val httpClient = OkHttpClient.Builder().build()

        suspend fun fetchMetadata(url: String): String = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("更新信息请求失败：${response.code}")
                }
                val body = response.body?.string().orEmpty().trim()
                if (body.isBlank()) {
                    throw IllegalStateException("更新信息为空")
                }
                body
            }
        }
    }
}

private fun Throwable.toUserFacingMessage(): String {
    return when (this) {
        is UnknownHostException -> "无法连接更新服务器，请检查网络后重试"
        is IOException -> "更新信息请求失败，请稍后重试"
        else -> message ?: "更新检查失败"
    }
}
