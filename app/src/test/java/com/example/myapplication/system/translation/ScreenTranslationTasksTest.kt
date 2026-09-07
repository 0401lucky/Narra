package com.example.myapplication.system.translation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenTranslationTasksTest {
    @Test
    fun close_duringLanguageSave_doesNotStartRetranslation() = runTest {
        val tasks = ScreenTranslationTasks(this)
        val saved = CompletableDeferred<Unit>()
        var translated = false
        tasks.launch {
            saved.await()
            translated = true
        }
        runCurrent()

        tasks.cancel()
        saved.complete(Unit)
        runCurrent()

        assertFalse(translated)
    }

    @Test
    fun nextTranslation_cancelsOldProducerBeforeItCanPublish() = runTest {
        val tasks = ScreenTranslationTasks(this)
        val oldResult = CompletableDeferred<String>()
        val published = mutableListOf<String>()
        tasks.launch { published += oldResult.await() }
        runCurrent()

        tasks.launch { published += "新译文" }
        oldResult.complete("旧译文")
        runCurrent()

        assertEquals(listOf("新译文"), published)
    }

    @Test
    fun capturePermission_duplicateRequestIsIgnored_andClosedResultIsRejected() = runTest {
        val tasks = ScreenTranslationTasks(this)
        val firstRequest = requireNotNull(tasks.beginCapturePermission())
        assertNull(tasks.beginCapturePermission())

        tasks.cancel()
        assertFalse(tasks.consumeCapturePermission(firstRequest))
        val nextRequest = requireNotNull(tasks.beginCapturePermission())
        assertFalse(tasks.consumeCapturePermission(firstRequest))
        assertTrue(tasks.consumeCapturePermission(nextRequest))
        assertFalse(tasks.consumeCapturePermission(nextRequest))
    }

    @Test
    fun selectedTextTranslation_invalidatesPendingScreenCapture() = runTest {
        val tasks = ScreenTranslationTasks(this)
        val request = requireNotNull(tasks.beginCapturePermission())
        var selectedTextStarted = false
        tasks.launch { selectedTextStarted = true }
        runCurrent()

        assertTrue(selectedTextStarted)
        assertFalse(tasks.consumeCapturePermission(request))
    }

    @Test
    fun newCapture_cancelsCurrentStream_andCloseCancelsLatestTask() = runTest {
        val tasks = ScreenTranslationTasks(this)
        var cancelled = 0
        tasks.launch {
            try {
                awaitCancellation()
            } finally {
                cancelled++
            }
        }
        runCurrent()
        tasks.beginCapturePermission()
        runCurrent()
        assertEquals(1, cancelled)

        tasks.launch {
            try {
                awaitCancellation()
            } finally {
                cancelled++
            }
        }
        runCurrent()
        tasks.cancel()
        runCurrent()
        assertEquals(2, cancelled)
    }
}
