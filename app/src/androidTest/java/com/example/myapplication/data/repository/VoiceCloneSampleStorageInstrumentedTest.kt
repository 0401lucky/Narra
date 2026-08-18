package com.example.myapplication.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VoiceCloneSampleStorageInstrumentedTest {
    @Test
    fun copyLocalFileToAppStorage_copiesPreviewIntoScopedSampleDirectory() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "voice-design-source.wav")
        val bytes = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0, 0, 0, 0,
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
            1, 2, 3,
        )
        source.writeBytes(bytes)

        var savedPath = ""
        try {
            val saved = VoiceCloneSampleStorage.copyLocalFileToAppStorage(
                context = context,
                sourcePath = source.absolutePath,
                scope = "assistant-storage-test",
            )
            savedPath = saved.path

            assertTrue(saved.path.startsWith(File(context.filesDir, "voice_clone_samples").absolutePath))
            assertArrayEquals(bytes, File(saved.path).readBytes())
        } finally {
            VoiceCloneSampleStorage.deleteIfLocal(context, savedPath)
            source.delete()
        }
    }

    @Test
    fun copyLocalFileToAppStorage_rejectsFileWithOnlyAudioExtension() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "voice-design-invalid.wav")
        source.writeText("not audio")

        try {
            val throwable = runCatching {
                VoiceCloneSampleStorage.copyLocalFileToAppStorage(
                    context = context,
                    sourcePath = source.absolutePath,
                    scope = "assistant-storage-test",
                )
            }.exceptionOrNull()

            assertTrue(throwable is IllegalArgumentException)
        } finally {
            source.delete()
        }
    }
}
