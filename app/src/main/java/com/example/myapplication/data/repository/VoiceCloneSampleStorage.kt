package com.example.myapplication.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

data class SavedVoiceCloneSample(
    val path: String,
    val mimeType: String,
    val fileName: String,
    val sizeBytes: Long,
)

object VoiceCloneSampleStorage {
    const val MAX_BASE64_CHARS = MAX_AUDIO_BASE64_CHARS
    private const val ROOT_DIR = "voice_clone_samples"
    private const val DEFAULT_SCOPE = "general"
    private const val MAX_RAW_BYTES = (MAX_BASE64_CHARS / 4) * 3

    suspend fun copyToAppStorage(
        context: Context,
        uri: Uri,
        scope: String = DEFAULT_SCOPE,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SavedVoiceCloneSample = withContext(dispatcher) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, uri).ifBlank { "voice-sample" }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_RAW_BYTES) {
                    throw IllegalArgumentException("音频样本太大，Base64 后不能超过 10 MB")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IOException("无法读取音频样本")
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("音频样本为空")
        }

        val detectedType = detectVoiceCloneAudioType(
            bytes = bytes,
            mimeType = runCatching { resolver.getType(uri) }.getOrNull(),
            fileName = displayName,
        ) ?: throw IllegalArgumentException("仅支持 wav 或 mp3 音频样本")

        saveBytesToAppStorage(
            context = context,
            bytes = bytes,
            detectedType = detectedType,
            displayName = displayName,
            scope = scope,
        )
    }

    suspend fun copyLocalFileToAppStorage(
        context: Context,
        sourcePath: String,
        scope: String = DEFAULT_SCOPE,
        displayName: String = "voice-design-preview.wav",
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SavedVoiceCloneSample = withContext(dispatcher) {
        val sourceFile = File(sourcePath.trim())
        if (!sourceFile.isFile) {
            throw IllegalArgumentException("试听音频文件不存在")
        }
        if (sourceFile.length() > MAX_RAW_BYTES) {
            throw IllegalArgumentException("音频样本太大，Base64 后不能超过 10 MB")
        }
        val bytes = sourceFile.readBytes()
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("试听音频文件为空")
        }
        val resolvedDisplayName = displayName.trim().ifBlank { sourceFile.name }
        val detectedType = detectVoiceCloneAudioType(
            bytes = bytes,
            mimeType = null,
            fileName = "",
        ) ?: throw IllegalArgumentException("试听音频不是可识别的 wav 或 mp3")

        saveBytesToAppStorage(
            context = context,
            bytes = bytes,
            detectedType = detectedType,
            displayName = resolvedDisplayName,
            scope = scope,
        )
    }

    private fun saveBytesToAppStorage(
        context: Context,
        bytes: ByteArray,
        detectedType: VoiceCloneAudioType,
        displayName: String,
        scope: String,
    ): SavedVoiceCloneSample {

        val directory = File(context.filesDir, "$ROOT_DIR/${sanitizeScope(scope)}")
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("无法创建声音克隆样本目录")
        }

        val safeBaseName = sanitizeFileName(displayName.substringBeforeLast('.', displayName))
        val target = File(directory, "${safeBaseName}-${UUID.randomUUID()}.${detectedType.extension}")
        target.writeBytes(bytes)

        return SavedVoiceCloneSample(
            path = target.absolutePath,
            mimeType = detectedType.mimeType,
            fileName = target.name,
            sizeBytes = bytes.size.toLong(),
        )
    }

    fun deleteIfLocal(context: Context, path: String?): Boolean {
        val baseDir = File(context.filesDir, ROOT_DIR)
        return deleteIfInsideBaseDir(baseDir, path)
    }

    private fun queryDisplayName(
        context: Context,
        uri: Uri,
    ): String {
        return context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index).orEmpty()
                } else {
                    ""
                }
            }
            .orEmpty()
    }

    private fun detectVoiceCloneAudioType(
        bytes: ByteArray,
        mimeType: String?,
        fileName: String,
    ): VoiceCloneAudioType? {
        val normalizedMime = mimeType?.trim()?.lowercase().orEmpty()
        val normalizedName = fileName.trim().lowercase()
        if (normalizedMime in listOf("audio/wav", "audio/x-wav") || normalizedName.endsWith(".wav")) {
            return VoiceCloneAudioType.WAV
        }
        if (normalizedMime in listOf("audio/mpeg", "audio/mp3") || normalizedName.endsWith(".mp3")) {
            return VoiceCloneAudioType.MP3
        }
        if (bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WAVE"
        ) {
            return VoiceCloneAudioType.WAV
        }
        if (bytes.size >= 3 && bytes.copyOfRange(0, 3).toString(Charsets.US_ASCII) == "ID3") {
            return VoiceCloneAudioType.MP3
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            (bytes[1].toInt() and 0xE0) == 0xE0
        ) {
            return VoiceCloneAudioType.MP3
        }
        return null
    }

    private fun sanitizeScope(scope: String): String {
        return scope.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_', '-').ifBlank {
            DEFAULT_SCOPE
        }
    }

    private fun sanitizeFileName(rawName: String): String {
        return rawName.trim()
            .replace(Regex("""[^\w.-]+"""), "_")
            .trim('_', '.', '-')
            .take(48)
            .ifBlank { "voice-sample" }
    }
}

private enum class VoiceCloneAudioType(
    val extension: String,
    val mimeType: String,
) {
    WAV("wav", "audio/wav"),
    MP3("mp3", "audio/mpeg"),
}
