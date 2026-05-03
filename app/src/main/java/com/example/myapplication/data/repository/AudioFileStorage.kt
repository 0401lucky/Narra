package com.example.myapplication.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.decodeBase64
import java.io.File
import java.io.IOException
import java.util.UUID

data class SavedAudioFile(
    val path: String,
    val mimeType: String,
    val fileName: String,
)

object AudioFileStorage {
    private const val AUDIO_DIR = "generated_voice_messages"
    private const val DATA_URI_BASE64_MARKER = ";base64,"
    private val BASE64_WHITESPACE = Regex("""\s+""")

    suspend fun saveBase64Audio(
        context: Context,
        b64Data: String,
        fileNamePrefix: String = UUID.randomUUID().toString(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SavedAudioFile {
        return saveBase64AudioToDirectory(
            directory = File(context.filesDir, AUDIO_DIR),
            b64Data = b64Data,
            fileNamePrefix = fileNamePrefix,
            dispatcher = dispatcher,
        )
    }

    suspend fun saveAudioBytes(
        context: Context,
        bytes: ByteArray,
        fileNamePrefix: String = UUID.randomUUID().toString(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SavedAudioFile {
        return saveAudioBytesToDirectory(
            directory = File(context.filesDir, AUDIO_DIR),
            bytes = bytes,
            fileNamePrefix = fileNamePrefix,
            dispatcher = dispatcher,
        )
    }

    suspend fun saveBase64AudioToDirectory(
        directory: File,
        b64Data: String,
        fileNamePrefix: String = UUID.randomUUID().toString(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SavedAudioFile = withContext(dispatcher) {
        val bytes = decodeBase64Audio(b64Data)
        writeAudioBytes(directory, bytes, fileNamePrefix)
    }

    suspend fun saveAudioBytesToDirectory(
        directory: File,
        bytes: ByteArray,
        fileNamePrefix: String = UUID.randomUUID().toString(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SavedAudioFile = withContext(dispatcher) {
        writeAudioBytes(directory, bytes, fileNamePrefix)
    }

    private fun writeAudioBytes(
        directory: File,
        bytes: ByteArray,
        fileNamePrefix: String,
    ): SavedAudioFile {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("无法创建语音文件目录")
        }
        val detectedAudioType = detectAudioType(bytes)
            ?: throw IllegalArgumentException("音频数据不是可识别的 WAV/MP3")
        val fileName = "${sanitizeFileNamePrefix(fileNamePrefix)}.${detectedAudioType.extension}"
        val file = File(directory, fileName)
        file.writeBytes(bytes)

        return SavedAudioFile(
            path = file.absolutePath,
            mimeType = detectedAudioType.mimeType,
            fileName = fileName,
        )
    }

    private fun decodeBase64Audio(b64Data: String): ByteArray {
        val normalizedData = b64Data
            .trim()
            .removeDataUriPrefix()
            .replace(BASE64_WHITESPACE, "")
        if (
            normalizedData.isBlank() ||
            normalizedData.length % 4 == 1
        ) {
            throw IllegalArgumentException("音频 Base64 数据无效")
        }
        val bytes = normalizedData.decodeBase64()?.toByteArray()
            ?: throw IllegalArgumentException("音频 Base64 数据无效")
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("音频 Base64 数据为空")
        }
        return bytes
    }

    private fun String.removeDataUriPrefix(): String {
        val markerIndex = indexOf(DATA_URI_BASE64_MARKER, ignoreCase = true)
        return if (markerIndex >= 0) {
            substring(markerIndex + DATA_URI_BASE64_MARKER.length)
        } else {
            this
        }
    }

    private fun sanitizeFileNamePrefix(rawValue: String): String {
        val sanitized = rawValue.trim()
            .replace(Regex("""[^\w.-]+"""), "_")
            .trim('_', '.', '-')
        return sanitized.ifBlank { UUID.randomUUID().toString() }
    }

    private fun detectAudioType(bytes: ByteArray): AudioType? {
        if (bytes.size >= 12) {
            val riff = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
            val wave = bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII)
            if (riff == "RIFF" && wave == "WAVE") {
                return AudioType.WAV
            }
        }
        if (bytes.size >= 3) {
            val id3 = bytes.copyOfRange(0, 3).toString(Charsets.US_ASCII)
            if (id3 == "ID3") {
                return AudioType.MP3
            }
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            (bytes[1].toInt() and 0xE0) == 0xE0
        ) {
            return AudioType.MP3
        }
        return null
    }
}

private enum class AudioType(
    val extension: String,
    val mimeType: String,
) {
    WAV(
        extension = "wav",
        mimeType = "audio/wav",
    ),
    MP3(
        extension = "mp3",
        mimeType = "audio/mpeg",
    ),
}
