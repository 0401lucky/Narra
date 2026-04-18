package com.example.myapplication.system.logging

import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLoggerTest {

    private lateinit var fakeSink: FakeSink
    private lateinit var originalSink: AppLogger.Sink

    @Before
    fun setUp() {
        fakeSink = FakeSink()
        originalSink = AppLogger.sink
        AppLogger.sink = fakeSink
    }

    @After
    fun tearDown() {
        AppLogger.sink = originalSink
    }

    @Test
    fun warn_forwardsMessageAndThrowableToSink() {
        val error = RuntimeException("boom")
        AppLogger.w("UnitTest", "something went wrong", error)

        assertEquals(1, fakeSink.events.size)
        val event = fakeSink.events.single()
        assertEquals(AppLogger.Level.WARN, event.level)
        assertEquals("UnitTest", event.tag)
        assertEquals("something went wrong", event.message)
        assertSame(error, event.throwable)
    }

    @Test
    fun logFailure_doesNothingOnSuccess() {
        val result: Result<String> = Result.success("ok")

        val returned = result.logFailure("UnitTest") { "should not be evaluated" }

        assertEquals("ok", returned.getOrNull())
        assertTrue(fakeSink.events.isEmpty())
    }

    @Test
    fun logFailure_logsOnFailureAndKeepsResultIntact() {
        val error = IllegalStateException("bad json")
        val result: Result<String> = Result.failure(error)

        val returned = result.logFailure("UnitTest") { "fromJson failed" }

        assertSame(error, returned.exceptionOrNull())
        assertEquals(1, fakeSink.events.size)
        val event = fakeSink.events.single()
        assertEquals(AppLogger.Level.WARN, event.level)
        assertEquals("fromJson failed", event.message)
        assertSame(error, event.throwable)
    }

    @Test
    fun logFailure_skipsCancellationException() {
        val result: Result<String> = Result.failure(CancellationException("coroutine cancelled"))

        result.logFailure("UnitTest") { "should not be called" }

        assertTrue(fakeSink.events.isEmpty())
    }

    @Test
    fun logFailure_lazyMessageOnlyEvaluatedOnFailure() {
        var evaluated = false
        val success: Result<Int> = Result.success(42)

        success.logFailure("UnitTest") {
            evaluated = true
            "won't run"
        }
        assertFalse(evaluated)

        val failure: Result<Int> = Result.failure(RuntimeException("err"))
        failure.logFailure("UnitTest") {
            evaluated = true
            "will run"
        }
        assertTrue(evaluated)
    }

    @Test
    fun d_noThrowable() {
        // debug 在 release 构建下不发事件，这里只验证接口可调；行为取决于 BuildConfig.DEBUG。
        AppLogger.d("UnitTest", "debug message")
        // 事件条数取决于构建配置；仅验证不抛异常即可。
        if (fakeSink.events.isNotEmpty()) {
            assertEquals(AppLogger.Level.DEBUG, fakeSink.events.first().level)
            assertNull(fakeSink.events.first().throwable)
            assertNotNull(fakeSink.events.first().message)
        }
    }

    private data class LogEvent(
        val level: AppLogger.Level,
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )

    private class FakeSink : AppLogger.Sink {
        val events = mutableListOf<LogEvent>()
        override fun log(
            level: AppLogger.Level,
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            events += LogEvent(level, tag, message, throwable)
        }
    }
}
