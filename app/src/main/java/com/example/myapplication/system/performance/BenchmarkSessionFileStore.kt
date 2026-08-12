package com.example.myapplication.system.performance

import android.content.Context
import android.util.AtomicFile
import com.example.myapplication.system.logging.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

class BenchmarkSessionFileStore(context: Context) {
    private val sessionFile = AtomicFile(
        File(context.noBackupFilesDir, "benchmark/session-v1.properties"),
    )

    @Synchronized
    fun replace(descriptor: BenchmarkSessionDescriptor): Result<Unit> {
        return runCatching {
            require(
                descriptor.validateForLaunch(
                    nowEpochMillis = descriptor.createdAtEpochMillis,
                    requestedSessionId = descriptor.sessionId,
                ) == null,
            ) { "benchmark session 描述无效" }
            sessionFile.baseFile.parentFile?.mkdirs()
            val bytes = BenchmarkSessionCodec.encode(descriptor).toByteArray(StandardCharsets.UTF_8)
            var output: FileOutputStream? = null
            try {
                output = sessionFile.startWrite()
                output.write(bytes)
                output.fd.sync()
                sessionFile.finishWrite(output)
            } catch (throwable: Throwable) {
                output?.let(sessionFile::failWrite)
                throw throwable
            }
        }.onFailure { throwable ->
            AppLogger.w(TAG, "benchmark session 写入失败", throwable)
        }
    }

    @Synchronized
    fun readOrNull(): BenchmarkSessionDescriptor? {
        if (!sessionFile.baseFile.exists()) {
            return null
        }
        return runCatching {
            val encoded = sessionFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            BenchmarkSessionCodec.decodeOrNull(encoded)
                ?: error("benchmark session 文件格式无效")
        }.onFailure { throwable ->
            AppLogger.w(TAG, "benchmark session 读取失败", throwable)
        }.getOrNull()
    }

    @Synchronized
    fun clear(): Result<Unit> {
        return runCatching {
            sessionFile.delete()
        }.onFailure { throwable ->
            AppLogger.w(TAG, "benchmark session 清理失败", throwable)
        }
    }

    private companion object {
        const val TAG = "BenchmarkSession"
    }
}

class BenchmarkRuntimePolicyResolver internal constructor(
    private val appChannel: String,
    private val sessionReader: () -> BenchmarkSessionDescriptor?,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    constructor(
        context: Context,
        appChannel: String,
        nowEpochMillis: () -> Long = System::currentTimeMillis,
    ) : this(
        appChannel = appChannel,
        sessionReader = BenchmarkSessionFileStore(context)::readOrNull,
        nowEpochMillis = nowEpochMillis,
    )

    fun resolve(requestedSessionId: String?): BenchmarkRuntimePolicy {
        if (appChannel != BenchmarkContract.BASELINE_CHANNEL) {
            return BenchmarkRuntimePolicy.resolve(
                appChannel = appChannel,
                descriptor = null,
                requestedSessionId = requestedSessionId,
                nowEpochMillis = nowEpochMillis(),
            )
        }
        return BenchmarkRuntimePolicy.resolve(
            appChannel = appChannel,
            descriptor = sessionReader(),
            requestedSessionId = requestedSessionId,
            nowEpochMillis = nowEpochMillis(),
        )
    }
}
