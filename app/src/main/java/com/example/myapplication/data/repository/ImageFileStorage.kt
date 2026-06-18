package com.example.myapplication.data.repository

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class SavedImageFile(
    val path: String,
    val mimeType: String,
    val fileName: String,
)

/**
 * 将 base64 图片数据保存到 app 私有目录，并返回正确的文件元信息。
 */
object ImageFileStorage {
    private const val IMAGE_DIR = "generated_images"

    suspend fun saveBase64Image(
        context: Context,
        b64Data: String,
        fileNamePrefix: String = UUID.randomUUID().toString(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SavedImageFile = withContext(dispatcher) {
        val dir = File(context.filesDir, IMAGE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        requireImageWithinLimit(estimateDecodedBase64Size(b64Data))
        val bytes = Base64.decode(b64Data, Base64.DEFAULT)
        requireImageWithinLimit(bytes.size)
        val detectedImageType = detectImageType(bytes)
            ?: throw IllegalArgumentException("图片数据无效，无法识别格式")
        val fileName = "${sanitizeFileNamePrefix(fileNamePrefix)}.${detectedImageType.extension}"
        val file = File(dir, fileName)
        file.writeBytes(bytes)

        SavedImageFile(
            path = file.absolutePath,
            mimeType = detectedImageType.mimeType,
            fileName = fileName,
        )
    }

    suspend fun saveImageBytes(
        context: Context,
        bytes: ByteArray,
        fileNamePrefix: String = UUID.randomUUID().toString(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SavedImageFile = withContext(dispatcher) {
        val dir = File(context.filesDir, IMAGE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        requireImageWithinLimit(bytes.size)
        val detectedImageType = detectImageType(bytes)
            ?: throw IllegalArgumentException("图片数据无效，无法识别格式")
        val fileName = "${sanitizeFileNamePrefix(fileNamePrefix)}.${detectedImageType.extension}"
        val file = File(dir, fileName)
        file.writeBytes(bytes)

        SavedImageFile(
            path = file.absolutePath,
            mimeType = detectedImageType.mimeType,
            fileName = fileName,
        )
    }

    private fun sanitizeFileNamePrefix(rawValue: String): String {
        val sanitized = rawValue.trim()
            .replace(Regex("""[^\w.-]+"""), "_")
            .trim('_', '.', '-')
        return sanitized.ifBlank { UUID.randomUUID().toString() }
    }
}
