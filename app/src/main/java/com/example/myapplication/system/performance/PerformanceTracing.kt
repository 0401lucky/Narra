package com.example.myapplication.system.performance

import android.os.Trace
import androidx.core.os.TraceCompat
import java.util.concurrent.atomic.AtomicInteger

private val nextAsyncTraceCookie = AtomicInteger(1)

internal fun <T> String.trace(
    block: () -> T,
): T {
    val traceStarted = try {
        Trace.beginSection(this)
        true
    } catch (_: RuntimeException) {
        false
    }
    return try {
        block()
    } finally {
        if (traceStarted) {
            try {
                Trace.endSection()
            } catch (_: RuntimeException) {
                // 本地 JVM 测试使用 Android stub；Trace 不应影响业务状态验证。
            }
        }
    }
}

@Suppress("DEPRECATION")
internal suspend fun <T> String.traceAsync(
    block: suspend () -> T,
): T {
    val cookie = nextAsyncTraceCookie.getAndIncrement()
    val traceStarted = try {
        TraceCompat.beginAsyncSection(this, cookie)
        true
    } catch (_: RuntimeException) {
        false
    }
    return try {
        block()
    } finally {
        if (traceStarted) {
            try {
                TraceCompat.endAsyncSection(this, cookie)
            } catch (_: RuntimeException) {
                // 异步 Trace 只提供观测能力，不得改变滚动与取消语义。
            }
        }
    }
}
