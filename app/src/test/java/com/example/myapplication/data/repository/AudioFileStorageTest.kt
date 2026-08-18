package com.example.myapplication.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class AudioFileStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun saveBase64AudioToDirectory_decodesAndSavesWavFile() = runTest {
        val audioBytes = byteArrayOf(
            'R'.code.toByte(),
            'I'.code.toByte(),
            'F'.code.toByte(),
            'F'.code.toByte(),
            0,
            0,
            0,
            0,
            'W'.code.toByte(),
            'A'.code.toByte(),
            'V'.code.toByte(),
            'E'.code.toByte(),
            1,
            2,
            3,
        )
        val encodedAudio = Base64.getEncoder().encodeToString(audioBytes)
        val directory = temporaryFolder.newFolder("voice")

        val savedAudio = AudioFileStorage.saveBase64AudioToDirectory(
            directory = directory,
            b64Data = "data:audio/wav;base64,\n${encodedAudio.chunked(4).joinToString("\n")}",
            fileNamePrefix = "voice:bad/name",
        )

        assertEquals("voice_bad_name.wav", savedAudio.fileName)
        assertEquals("audio/wav", savedAudio.mimeType)
        assertArrayEquals(audioBytes, File(savedAudio.path).readBytes())
    }

    @Test
    fun saveBase64AudioToDirectory_rejectsInvalidBase64() = runTest {
        val directory = temporaryFolder.newFolder("voice")

        val failure = runCatching {
            AudioFileStorage.saveBase64AudioToDirectory(
                directory = directory,
                b64Data = "not-valid-base64",
                fileNamePrefix = "voice",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun normalizeBase64AudioData_acceptsLimitMinusOneAndExactLimit() {
        val limit = 8

        assertEquals(limit - 1, normalizeBase64AudioData("A".repeat(limit - 1), limit).length)
        assertEquals(limit, normalizeBase64AudioData("A".repeat(limit), limit).length)
    }

    @Test
    fun normalizeBase64AudioData_rejectsLimitPlusOne() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            normalizeBase64AudioData("A".repeat(9), maxChars = 8)
        }

        assertTrue(error.message.orEmpty().contains("Base64 数据过大"))
    }
}
