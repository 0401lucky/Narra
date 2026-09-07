package com.example.myapplication.system.translation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 屏幕授权和翻译共用一个取消边界，关闭后不接受迟到的授权结果。 */
internal class ScreenTranslationTasks(private val scope: CoroutineScope) {
    private var job: Job? = null
    private var captureSequence = 0L
    private var pendingCapture: Long? = null

    fun launch(block: suspend CoroutineScope.() -> Unit) {
        cancel()
        val next = scope.launch(start = CoroutineStart.LAZY, block = block)
        job = next
        next.start()
    }

    fun beginCapturePermission(): Long? {
        if (pendingCapture != null) return null
        cancel()
        return (++captureSequence).also { pendingCapture = it }
    }

    fun consumeCapturePermission(requestId: Long): Boolean {
        if (requestId != pendingCapture) return false
        pendingCapture = null
        return true
    }

    fun cancel() {
        pendingCapture = null
        job?.cancel()
        job = null
    }
}
