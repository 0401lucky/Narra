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
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SavedImageFile = withContext(dispatcher) {
        val dir = File(context.filesDir, IMAGE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val bytes = Base64.decode(b64Data, Base64.DEFAULT)
        val detectedImageType = detectImageType(bytes)
        val fileName = "${UUID.randomUUID()}.${detectedImageType.extension}"
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

        val detectedImageType = detectImageType(bytes)
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

    private fun detectImageType(bytes: ByteArray): ImageType {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return ImageType.PNG
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return ImageType.JPEG
        }
        if (bytes.size >= 6) {
            val header = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
            if (header == "GIF87a" || header == "GIF89a") {
                return ImageType.GIF
            }
        }
        if (bytes.size >= 12) {
            val riff = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
            val webp = bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII)
            if (riff == "RIFF" && webp == "WEBP") {
                return ImageType.WEBP
            }
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0x42.toByte() &&
            bytes[1] == 0x4D.toByte()
        ) {
            return ImageType.BMP
        }

        val textHeader = bytes.take(128).toByteArray()
            .toString(Charsets.UTF_8)
            .trimStart()
            .lowercase()
        if (textHeader.startsWith("<svg")) {
            return ImageType.SVG
        }

        return ImageType.PNG
    }
}

private enum class ImageType(
    val extension: String,
    val mimeType: String,
) {
    PNG(
        extension = "png",
        mimeType = "image/png",
    ),
    JPEG(
        extension = "jpg",
        mimeType = "image/jpeg",
    ),
    GIF(
        extension = "gif",
        mimeType = "image/gif",
    ),
    WEBP(
        extension = "webp",
        mimeType = "image/webp",
    ),
    BMP(
        extension = "bmp",
        mimeType = "image/bmp",
    ),
    SVG(
        extension = "svg",
        mimeType = "image/svg+xml",
    ),
}
