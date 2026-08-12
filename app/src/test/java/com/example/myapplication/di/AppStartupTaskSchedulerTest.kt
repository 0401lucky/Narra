package com.example.myapplication.di

import com.example.myapplication.system.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppStartupTaskSchedulerTest {
    private val logEvents = mutableListOf<LogEvent>()
    private lateinit var originalLogSink: AppLogger.Sink

    @Before
    fun setUp() {
        originalLogSink = AppLogger.sink
        AppLogger.sink = AppLogger.Sink { level, tag, message, throwable ->
            logEvents += LogEvent(level, tag, message, throwable)
        }
    }

    @After
    fun tearDown() {
        AppLogger.sink = originalLogSink
    }

    @Test
    fun allTasksSucceed_runsEveryTaskAndEmitsStableTraceNames() = runTest {
        val harness = createHarness()
        val executedTasks = mutableListOf<AppStartupTask>()

        try {
            val jobs = AppStartupTask.values().map { task ->
                if (task == AppStartupTask.CONTEXT_LOG_SETTINGS) {
                    harness.scheduler.schedule(task, traceTaskLifetime = false) {
                        harness.scheduler.trace(task) {
                            executedTasks += task
                        }
                    }
                } else {
                    harness.scheduler.schedule(task) {
                        executedTasks += task
                    }
                }
            }
            advanceUntilIdle()

            assertEquals(AppStartupTask.values().toList(), executedTasks)
            assertTrue(jobs.all { it.isCompleted && !it.isCancelled })
            assertEquals(
                listOf("SensitiveMigration", "EnsurePresets", "ContextLogSettings"),
                AppStartupTask.values().map(AppStartupTask::taskName),
            )
            assertEquals(
                listOf(
                    "narra.startup.sensitive_migration",
                    "narra.startup.ensure_presets",
                    "narra.startup.context_log_settings",
                ),
                harness.traceSections,
            )
            assertTrue(logEvents.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun sensitiveMigrationFailure_logsStableTaskNameAndRedactsThrowable() = runTest {
        verifyFailure(
            task = AppStartupTask.SENSITIVE_MIGRATION,
            expectedTaskName = "SensitiveMigration",
            expectedTraceName = "narra.startup.sensitive_migration",
            secret = "sk-sensitive12345678",
        )
    }

    @Test
    fun ensurePresetsFailure_logsStableTaskNameAndRedactsThrowable() = runTest {
        verifyFailure(
            task = AppStartupTask.ENSURE_PRESETS,
            expectedTaskName = "EnsurePresets",
            expectedTraceName = "narra.startup.ensure_presets",
            secret = "sk-presets123456789",
        )
    }

    @Test
    fun contextLogSettingsFailure_logsStableTaskNameAndRedactsThrowable() = runTest {
        verifyFailure(
            task = AppStartupTask.CONTEXT_LOG_SETTINGS,
            expectedTaskName = "ContextLogSettings",
            expectedTraceName = "narra.startup.context_log_settings",
            secret = "sk-contextlog123456",
            traceInsideTask = true,
        )
    }

    @Test
    fun cancellation_isRethrownWithoutFailureLogAndSiblingStillRuns() = runTest {
        val harness = createHarness()
        val cancellation = CancellationException("cancel startup task")
        var completionCause: Throwable? = null
        var siblingRan = false

        try {
            val cancelledJob = harness.scheduler.schedule(AppStartupTask.SENSITIVE_MIGRATION) {
                throw cancellation
            }.also { job ->
                job.invokeOnCompletion { throwable ->
                    completionCause = throwable
                }
            }
            val siblingJob = harness.scheduler.schedule(AppStartupTask.ENSURE_PRESETS) {
                siblingRan = true
            }
            advanceUntilIdle()

            assertTrue(cancelledJob.isCancelled)
            assertSame(cancellation, completionCause)
            assertTrue(siblingJob.isCompleted && !siblingJob.isCancelled)
            assertTrue(siblingRan)
            assertTrue(logEvents.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun eachTaskFailure_doesNotStopSiblingTasks() = runTest {
        val harness = createHarness()

        try {
            AppStartupTask.values().forEach { failingTask ->
                val executedTasks = mutableSetOf<AppStartupTask>()
                val jobs = AppStartupTask.values().map { task ->
                    harness.scheduler.schedule(task) {
                        executedTasks += task
                        if (task == failingTask) {
                            throw IllegalStateException("failure in ${task.taskName}")
                        }
                    }
                }
                advanceUntilIdle()

                assertEquals(AppStartupTask.values().toSet(), executedTasks)
                assertTrue(jobs.all { it.isCompleted && !it.isCancelled })
            }
            assertEquals(AppStartupTask.values().size, logEvents.size)
        } finally {
            harness.close()
        }
    }

    private suspend fun TestScope.verifyFailure(
        task: AppStartupTask,
        expectedTaskName: String,
        expectedTraceName: String,
        secret: String,
        traceInsideTask: Boolean = false,
    ) {
        val harness = createHarness()

        try {
            val job = harness.scheduler.schedule(
                task = task,
                traceTaskLifetime = !traceInsideTask,
            ) {
                if (traceInsideTask) {
                    harness.scheduler.trace(task) {
                        throw IllegalStateException("{\"api_key\":\"$secret\"}")
                    }
                } else {
                    throw IllegalStateException("{\"api_key\":\"$secret\"}")
                }
            }
            advanceUntilIdle()

            assertTrue(job.isCompleted && !job.isCancelled)
            assertEquals(listOf(expectedTraceName), harness.traceSections)
            assertEquals(1, logEvents.size)
            val event = logEvents.single()
            assertEquals(AppLogger.Level.WARN, event.level)
            assertEquals("AppGraph", event.tag)
            assertEquals("Startup task failed: $expectedTaskName", event.message)
            assertFalse(event.message.contains(secret))
            assertFalse(event.throwable.toString().contains(secret))
            assertTrue(event.throwable.toString().contains("[REDACTED]"))
        } finally {
            harness.close()
        }
    }

    private fun TestScope.createHarness(): SchedulerHarness {
        val traceSections = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = AppStartupTaskScheduler(
            scope = scope,
            traceRunner = AppStartupTraceRunner { sectionName, block ->
                traceSections += sectionName
                block()
            },
        )
        return SchedulerHarness(
            scope = scope,
            scheduler = scheduler,
            traceSections = traceSections,
        )
    }

    private data class LogEvent(
        val level: AppLogger.Level,
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )

    private data class SchedulerHarness(
        val scope: CoroutineScope,
        val scheduler: AppStartupTaskScheduler,
        val traceSections: MutableList<String>,
    ) {
        fun close() {
            scope.cancel()
        }
    }
}
