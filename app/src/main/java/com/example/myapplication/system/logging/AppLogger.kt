package com.example.myapplication.system.logging

import android.util.Log
import com.example.myapplication.BuildConfig

/**
 * 全局轻量 Logger。
 *
 * 目的：让散落在 140+ 处 `runCatching { … }.getOrNull()` 的异常不再被完全静默吞掉。
 * 在 debug 构建下直接打 `android.util.Log`；release 构建下 warn/error 仍会打，但可后续接
 * Crashlytics / 自建上报通道（见 [sink]）。
 *
 * 使用姿势：
 * - 普通打点：`AppLogger.w("AiGateway", "request failed", t)`。
 * - 结合 Result：`runCatching { … }.logFailure("SearchRepo") { "fromJson failed" }.getOrNull()`。
 *
 * 绝对不要把 Logger 当作业务错误通道 —— 它只描述"发生了意料外的异常，且我们选择吞掉它继续"。
 */
object AppLogger {
    /** 未来可把埋点/上报器注入进来，默认使用 Logcat。 */
    @Volatile
    var sink: Sink = LogcatSink

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) sink.log(Level.DEBUG, tag, message, null)
    }

    fun i(tag: String, message: String) {
        sink.log(Level.INFO, tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        sink.log(Level.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        sink.log(Level.ERROR, tag, message, throwable)
    }

    enum class Level { DEBUG, INFO, WARN, ERROR }

    fun interface Sink {
        fun log(level: Level, tag: String, message: String, throwable: Throwable?)
    }

    private object LogcatSink : Sink {
        override fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
            val safeTag = tag.take(23) // Logcat tag 长度限制
            // 单元测试环境下 android.util.Log 未 mock 时调用会抛 RuntimeException，
            // 这里兜底吞掉避免污染业务路径 —— 日志 sink 本就不该成为故障源。
            runCatching {
                when (level) {
                    Level.DEBUG -> Log.d(safeTag, message, throwable)
                    Level.INFO -> Log.i(safeTag, message, throwable)
                    Level.WARN -> Log.w(safeTag, message, throwable)
                    Level.ERROR -> Log.e(safeTag, message, throwable)
                }
            }
        }
    }
}

/**
 * 链式记录 Result 失败：仅在失败时求值 lazy message。
 *
 * ```
 * val parsed = runCatching { gson.fromJson<X>(raw) }
 *     .logFailure("SearchRepo") { "parse sources failed, raw.len=${raw.length}" }
 *     .getOrNull()
 * ```
 *
 * - CancellationException 不记录（协程取消是正常控制流）。
 * - 默认用 WARN 级别；需要 ERROR 的场景直接手写 `AppLogger.e(...)`。
 */
inline fun <T> Result<T>.logFailure(
    tag: String,
    lazyMessage: () -> String,
): Result<T> {
    exceptionOrNull()?.let { throwable ->
        if (throwable is kotlinx.coroutines.CancellationException) return this
        AppLogger.w(tag, lazyMessage(), throwable)
    }
    return this
}
