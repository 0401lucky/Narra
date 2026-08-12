package com.example.myapplication.system.performance

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Trace
import com.example.myapplication.BuildConfig
import com.example.myapplication.system.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class BenchmarkFixtureProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        val appContext = context?.applicationContext
            ?: return BenchmarkFixtureProtocol.failure(
                BenchmarkFixtureErrorCode.PREPARE_FAILED,
                "benchmark 控制组件未取得应用上下文",
            )
        if (BuildConfig.APP_CHANNEL != BenchmarkContract.BASELINE_CHANNEL) {
            return BenchmarkFixtureProtocol.failure(
                BenchmarkFixtureErrorCode.WRONG_CHANNEL,
                "benchmark 控制仅允许 baseline 渠道",
            )
        }
        val sessionStore = BenchmarkSessionFileStore(appContext)
        return when (method) {
            BenchmarkFixtureProtocol.METHOD_PREPARE -> prepare(extras, sessionStore)
            BenchmarkFixtureProtocol.METHOD_CLEAR_SESSION -> {
                val cleared = sessionStore.clear()
                if (cleared.isSuccess) {
                    BenchmarkFixtureProtocol.successWithSession(null)
                } else {
                    BenchmarkFixtureProtocol.failure(
                        BenchmarkFixtureErrorCode.SESSION_WRITE_FAILED,
                        "benchmark session 清理失败",
                    )
                }
            }

            BenchmarkFixtureProtocol.METHOD_READ_SESSION -> {
                BenchmarkFixtureProtocol.successWithSession(sessionStore.readOrNull())
            }

            else -> BenchmarkFixtureProtocol.failure(
                BenchmarkFixtureErrorCode.UNKNOWN_METHOD,
                "不支持的 benchmark 控制方法",
            )
        }
    }

    private fun prepare(
        extras: Bundle?,
        sessionStore: BenchmarkSessionFileStore,
    ): Bundle {
        val encoded = extras?.getString(BenchmarkFixtureProtocol.KEY_SESSION_DESCRIPTOR)
            ?: return BenchmarkFixtureProtocol.failure(
                BenchmarkFixtureErrorCode.MISSING_DESCRIPTOR,
                "缺少 benchmark session 描述",
            )
        val descriptor = BenchmarkSessionCodec.decodeOrNull(encoded)
            ?: return BenchmarkFixtureProtocol.failure(
                BenchmarkFixtureErrorCode.INVALID_DESCRIPTOR,
                "benchmark session 描述格式无效",
            )
        val rejection = descriptor.validateForLaunch(
            nowEpochMillis = System.currentTimeMillis(),
            requestedSessionId = descriptor.sessionId,
        )
        if (rejection != null) {
            return BenchmarkFixtureProtocol.failure(
                BenchmarkFixtureErrorCode.INVALID_DESCRIPTOR,
                "benchmark session 被拒绝：${rejection.name.lowercase()}",
            )
        }
        Trace.beginSection(PerformanceTraceContract.FIXTURE_PREPARE)
        return try {
            val summary = runBlocking(Dispatchers.IO) {
                BaselineFixturePreparer(requireNotNull(context).applicationContext).prepare(descriptor.scenarioId.fixtureId)
            }
            val stored = sessionStore.replace(descriptor)
            if (stored.isFailure) {
                BenchmarkFixtureProtocol.failure(
                    BenchmarkFixtureErrorCode.SESSION_WRITE_FAILED,
                    "benchmark session 写入失败",
                )
            } else {
                BenchmarkFixtureProtocol.success(descriptor, summary)
            }
        } catch (throwable: Throwable) {
            sessionStore.clear()
            AppLogger.e(TAG, "benchmark fixture 准备失败", throwable)
            BenchmarkFixtureProtocol.failure(
                BenchmarkFixtureErrorCode.PREPARE_FAILED,
                "benchmark fixture 准备失败",
            )
        } finally {
            Trace.endSection()
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        const val TAG = "BenchmarkFixture"
    }
}
