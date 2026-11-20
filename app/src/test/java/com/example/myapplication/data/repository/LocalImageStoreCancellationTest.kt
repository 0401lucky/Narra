package com.example.myapplication.data.repository

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalImageStoreCancellationTest {
    @Test
    fun cancelAtEof_closesStreamAndRemovesOnlyNewTarget() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val source = File(
            application.cacheDir,
            "copy-${UUID.randomUUID()}.bin"
        ).apply { writeBytes(ByteArray(16_384) { (it % 127).toByte() }) }
        val directory = File(application.filesDir, "user_images/cancel_test").apply { mkdirs() }
        val shared = File(
            directory,
            "shared-${UUID.randomUUID()}.png"
        ).apply { writeText("existing user file") }
        val before = directory.walkTopDown().map { it.canonicalPath }.toSet()
        val uri = Uri.parse("content://copy-cancel/${UUID.randomUUID()}")
        var closed = false
        var reachedEof = false
        lateinit var copy: Deferred<String?>
        shadowOf(application.contentResolver).registerInputStreamSupplier(uri) {
            object : FileInputStream(source) {
                override fun read(buffer: ByteArray): Int = read(buffer, 0, buffer.size)
                override fun read(buffer: ByteArray, offset: Int, count: Int): Int =
                    super.read(buffer, offset, count).also {
                        if (it < 0) {
                            reachedEof = true; copy.cancel()
                        }
                    }

                override fun close() {
                    closed = true; super.close()
                }
            }
        }
        val store = LocalImageStore(application, StandardTestDispatcher(testScheduler))
        copy = async(start = CoroutineStart.LAZY) { store.copyToAppStorage(uri, "cancel_test") }
        copy.start()
        assertTrue(runCatching { copy.await() }.exceptionOrNull() is CancellationException)
        advanceUntilIdle()
        assertTrue(reachedEof)
        assertTrue(closed)
        assertEquals(before, directory.walkTopDown().map { it.canonicalPath }.toSet())
        assertEquals("existing user file", shared.readText())
        assertEquals(16_384, source.length().toInt())
    }

    @Test
    fun encodedFileUri_normalizesToTheSameProtectedFile() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val file = File(
            application.filesDir,
            "user_images/汉 字-${UUID.randomUUID()}.png"
        ).apply { parentFile!!.mkdirs(); writeText("owned") }
        assertEquals(
            file.canonicalPath,
            File(requireNotNull(normalizeImagePath(file.toURI().toString()))).canonicalPath
        )
        val sibling =
            File(file.parentFile, "shared-${UUID.randomUUID()}.png").apply { writeText("shared") }
        assertTrue(
            deleteIfInsideBaseDir(
                File(application.filesDir, "user_images"),
                file.toURI().toString()
            )
        )
        assertFalse(file.exists())
        assertEquals("shared", sibling.readText())
    }
}
