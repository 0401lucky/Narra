package com.example.myapplication.data.repository

import android.content.Context
import androidx.core.net.toUri
import com.example.myapplication.model.MessageAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class FileAttachmentResolver(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun resolvePromptText(attachment: MessageAttachment): String = withContext(dispatcher) {
        val uri = attachment.uri.toUri()
        val fileName = attachment.fileName.ifBlank { "未命名文件" }
        val mimeType = attachment.mimeType.ifBlank {
            context.contentResolver.getType(uri).orEmpty()
        }.ifBlank {
            DEFAULT_MIME_TYPE
        }

        val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val chunk = ByteArray(8 * 1024)
            val output = ByteArrayOutputStream()
            var totalBytes = 0
            while (true) {
                val readCount = input.read(chunk)
                if (readCount <= 0) {
                    break
                }
                val remaining = MAX_FILE_BYTES - totalBytes
                if (remaining <= 0) {
                    break
                }
                val copyCount = minOf(readCount, remaining)
                output.write(chunk, 0, copyCount)
                totalBytes += copyCount
                if (copyCount < readCount) {
                    break
                }
            }
            output.toByteArray()
        } ?: throw IllegalStateException("无法读取所选文件")

        if (!isSupportedTextFile(fileName = fileName, mimeType = mimeType, content = rawBytes)) {
            throw IllegalStateException("当前仅支持上传文本类文件和图片")
        }

        val decoded = rawBytes.toString(Charsets.UTF_8)
            .replace("\u0000", "")
            .trim()
        if (decoded.isBlank()) {
            throw IllegalStateException("所选文件没有可读取的文本内容")
        }

        val truncatedText = decoded.take(MAX_TEXT_CHARS)
        val isTruncated = decoded.length > MAX_TEXT_CHARS || rawBytes.size >= MAX_FILE_BYTES

        buildString {
            append("用户上传了一个文件，请结合文件内容回答。")
            append("\n文件名：").append(fileName)
            append("\n文件类型：").append(mimeType)
            if (isTruncated) {
                append("\n注意：文件较长，以下仅包含前 ").append(MAX_TEXT_CHARS).append(" 个字符。")
            }
            append("\n文件内容：\n```")
            append("\n").append(truncatedText)
            if (!truncatedText.endsWith('\n')) {
                append("\n")
            }
            append("```")
        }
    }

    private fun isSupportedTextFile(
        fileName: String,
        mimeType: String,
        content: ByteArray,
    ): Boolean {
        val normalizedName = fileName.lowercase()
        val normalizedMimeType = mimeType.lowercase()
        if (normalizedMimeType.startsWith("image/")) {
            return false
        }
        if (normalizedName.endsWith(".pdf") ||
            normalizedName.endsWith(".doc") ||
            normalizedName.endsWith(".docx") ||
            normalizedName.endsWith(".xls") ||
            normalizedName.endsWith(".xlsx") ||
            normalizedName.endsWith(".ppt") ||
            normalizedName.endsWith(".pptx") ||
            normalizedName.endsWith(".zip") ||
            normalizedName.endsWith(".rar") ||
            normalizedName.endsWith(".7z") ||
            normalizedName.endsWith(".apk")
        ) {
            return false
        }
        if (normalizedMimeType == "application/pdf" ||
            normalizedMimeType.contains("word") ||
            normalizedMimeType.contains("excel") ||
            normalizedMimeType.contains("spreadsheet") ||
            normalizedMimeType.contains("presentation") ||
            normalizedMimeType.contains("zip")
        ) {
            return false
        }
        if (content.contains(0.toByte())) {
            return false
        }

        val sample = content.take(2_048)
        if (sample.isEmpty()) {
            return true
        }
        val controlCount = sample.count { byte ->
            val value = byte.toInt() and 0xFF
            value < 0x09 || value in 0x0E..0x1F
        }
        return controlCount <= sample.size / 10
    }

    private companion object {
        const val DEFAULT_MIME_TYPE = "text/plain"
        const val MAX_FILE_BYTES = 256 * 1024
        const val MAX_TEXT_CHARS = 12_000
    }
}
