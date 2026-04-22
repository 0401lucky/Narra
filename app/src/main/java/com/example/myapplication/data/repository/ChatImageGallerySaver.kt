package com.example.myapplication.data.repository

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class LoadedChatImage(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
)

sealed interface SaveImageResult {
    data class Success(
        val savedFileName: String,
    ) : SaveImageResult

    data class Failure(
        val message: String,
    ) : SaveImageResult
}

internal interface ChatImageSourceReader {
    suspend fun readFile(path: String): LoadedChatImage

    suspend fun readContentUri(uri: String): LoadedChatImage

    suspend fun download(url: String): LoadedChatImage
}

internal interface ChatImageGalleryWriter {
    suspend fun write(image: LoadedChatImage): SaveImageResult
}

internal class ChatImageGallerySaver(
    private val sourceReader: ChatImageSourceReader,
    private val galleryWriter: ChatImageGalleryWriter,
    private val fileNamePrefixProvider: () -> String = { "narra-image-${System.currentTimeMillis()}" },
) {
    suspend fun save(
        source: String,
        displayName: String = "",
    ): SaveImageResult {
        val loadedImage = runCatching {
            resolveSource(
                source = source,
                displayName = displayName,
            )
        }.getOrElse { throwable ->
            return SaveImageResult.Failure(throwable.toUserMessage())
        }
        return galleryWriter.write(loadedImage)
    }

    private suspend fun resolveSource(
        source: String,
        displayName: String,
    ): LoadedChatImage {
        val trimmedSource = source.trim()
        if (trimmedSource.isBlank()) {
            throw IllegalArgumentException("图片地址为空")
        }
        return when {
            trimmedSource.startsWith("http://", ignoreCase = true) ||
                trimmedSource.startsWith("https://", ignoreCase = true) -> {
                runCatching {
                    sourceReader.download(trimmedSource)
                }.getOrElse { throwable ->
                    throw IllegalStateException(
                        throwable.message
                            ?.takeIf { it.startsWith("下载图片失败：") }
                            ?: "下载图片失败：${throwable.message.orEmpty().ifBlank { "网络异常" }}",
                    )
                }.withResolvedFileName(displayName, fileNamePrefixProvider)
            }

            trimmedSource.startsWith("content://", ignoreCase = true) -> {
                sourceReader.readContentUri(trimmedSource).withResolvedFileName(displayName, fileNamePrefixProvider)
            }

            trimmedSource.startsWith("data:image", ignoreCase = true) -> {
                decodeDataImage(trimmedSource, displayName)
            }

            else -> {
                val normalizedPath = normalizeImagePath(trimmedSource)
                    ?: throw IllegalArgumentException("图片文件不存在或无法读取")
                sourceReader.readFile(normalizedPath).withResolvedFileName(displayName, fileNamePrefixProvider)
            }
        }
    }
}

class AndroidChatImageSourceReader(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ChatImageSourceReader {
    override suspend fun readFile(path: String): LoadedChatImage = withContext(dispatcher) {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("图片文件不存在或无法读取")
        }
        val bytes = runCatching { file.readBytes() }
            .getOrElse { throw IllegalArgumentException("图片文件不存在或无法读取") }
        LoadedChatImage(
            bytes = bytes,
            mimeType = resolveImageMimeType(
                declaredMimeType = guessMimeTypeFromFileName(file.name),
                bytes = bytes,
            ),
            fileName = file.name,
        )
    }

    override suspend fun readContentUri(uri: String): LoadedChatImage = withContext(dispatcher) {
        val contentUri = uri.toUri()
        val resolver = context.contentResolver
        val mimeType = runCatching { resolver.getType(contentUri) }.getOrNull().orEmpty()
        val bytes = resolver.openInputStream(contentUri)?.use { input -> input.readBytes() }
            ?: throw IllegalArgumentException("图片内容无法读取")
        LoadedChatImage(
            bytes = bytes,
            mimeType = resolveImageMimeType(mimeType, bytes),
            fileName = queryDisplayName(contentUri).orEmpty(),
        )
    }

    override suspend fun download(url: String): LoadedChatImage = withContext(dispatcher) {
        val request = Request.Builder()
            .url(url)
            .build()
        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                val responseBody = response.body ?: throw IllegalStateException("响应体为空")
                val bytes = responseBody.bytes()
                LoadedChatImage(
                    bytes = bytes,
                    mimeType = resolveImageMimeType(
                        declaredMimeType = responseBody.contentType()?.toString().orEmpty(),
                        bytes = bytes,
                    ),
                    fileName = resolveRemoteFileName(url),
                )
            }
        }.getOrElse { throwable ->
            throw IllegalStateException("下载图片失败：${throwable.message.orEmpty().ifBlank { "网络异常" }}")
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        val resolver = context.contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        return@use null
                    }
                    cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                }
        }.getOrNull()
    }
}

class AndroidChatImageGalleryWriter(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ChatImageGalleryWriter {
    override suspend fun write(image: LoadedChatImage): SaveImageResult = withContext(dispatcher) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeWithMediaStore(image)
            } else {
                writeToLegacyPictures(image)
            }
        }.getOrElse { throwable ->
            SaveImageResult.Failure(
                throwable.message?.trim().orEmpty().ifBlank { "保存图片失败" },
            )
        }
    }

    private fun writeWithMediaStore(image: LoadedChatImage): SaveImageResult {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, sanitizeImageFileName(image.fileName))
            put(MediaStore.Images.Media.MIME_TYPE, image.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Narra")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val targetUri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues,
        ) ?: return SaveImageResult.Failure("无法写入系统相册")

        return runCatching {
            resolver.openOutputStream(targetUri)?.use { output ->
                output.write(image.bytes)
            } ?: error("无法写入系统相册")
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(targetUri, contentValues, null, null)
            SaveImageResult.Success(sanitizeImageFileName(image.fileName))
        }.getOrElse { throwable ->
            resolver.delete(targetUri, null, null)
            SaveImageResult.Failure(throwable.message ?: "无法写入系统相册")
        }
    }

    @Suppress("DEPRECATION")
    private fun writeToLegacyPictures(image: LoadedChatImage): SaveImageResult {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return SaveImageResult.Failure("没有存储权限，无法保存到相册")
        }
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val narraDir = File(picturesDir, "Narra")
        if (!narraDir.exists() && !narraDir.mkdirs() && !narraDir.isDirectory) {
            return SaveImageResult.Failure("无法创建相册目录")
        }
        val targetFile = buildUniqueTargetFile(
            directory = narraDir,
            fileName = sanitizeImageFileName(image.fileName),
        )
        return runCatching {
            targetFile.writeBytes(image.bytes)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(image.mimeType),
                null,
            )
            SaveImageResult.Success(targetFile.name)
        }.getOrElse { throwable ->
            SaveImageResult.Failure(throwable.message ?: "保存图片失败")
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeDataImage(
    source: String,
    displayName: String,
): LoadedChatImage {
    val commaIndex = source.indexOf(',')
    if (commaIndex <= 0) {
        throw IllegalArgumentException("图片数据无效，无法保存")
    }
    val header = source.substring(0, commaIndex)
    if (!header.contains(";base64", ignoreCase = true)) {
        throw IllegalArgumentException("图片数据无效，无法保存")
    }
    val mimeType = header.substringAfter("data:", "").substringBefore(';').trim()
    val bytes = runCatching {
        Base64.decode(source.substring(commaIndex + 1))
    }.getOrElse {
        throw IllegalArgumentException("图片数据无效，无法保存")
    }
    return LoadedChatImage(
        bytes = bytes,
        mimeType = resolveImageMimeType(mimeType, bytes),
        fileName = buildResolvedFileName(
            currentName = displayName.trim(),
            fallbackPrefix = "narra-image-${System.currentTimeMillis()}",
            mimeType = mimeType,
            bytes = bytes,
        ),
    )
}

private fun LoadedChatImage.withResolvedFileName(
    displayName: String,
    fileNamePrefixProvider: () -> String,
): LoadedChatImage {
    return copy(
        mimeType = resolveImageMimeType(mimeType, bytes),
        fileName = buildResolvedFileName(
            currentName = if (displayName.isNotBlank()) displayName else fileName,
            fallbackPrefix = fileNamePrefixProvider(),
            mimeType = mimeType,
            bytes = bytes,
        ),
    )
}

private fun buildResolvedFileName(
    currentName: String,
    fallbackPrefix: String,
    mimeType: String,
    bytes: ByteArray,
): String {
    val trimmedName = currentName.trim()
    val normalizedName = if (trimmedName.isBlank()) "" else sanitizeImageFileName(trimmedName)
    if (normalizedName.contains('.') && normalizedName.substringAfterLast('.').isNotBlank()) {
        return normalizedName
    }
    val extension = resolveImageExtensionForSave(mimeType, bytes)
    val resolvedPrefix = normalizedName.substringBeforeLast('.', normalizedName).ifBlank {
        fallbackPrefix.trim().ifBlank { "narra-image" }
    }
    return "${resolvedPrefix.trimEnd('.')}.${extension}"
}

private fun sanitizeImageFileName(rawValue: String): String {
    return rawValue.trim()
        .replace(Regex("""[^\w.-]+"""), "_")
        .trim('_', '.', '-')
        .ifBlank { "narra-image" }
}

private fun resolveImageExtensionForSave(
    mimeType: String,
    bytes: ByteArray,
): String {
    val normalizedMimeType = mimeType.trim().lowercase()
    return when {
        "png" in normalizedMimeType -> "png"
        "jpeg" in normalizedMimeType || "jpg" in normalizedMimeType -> "jpg"
        "webp" in normalizedMimeType -> "webp"
        "gif" in normalizedMimeType -> "gif"
        "bmp" in normalizedMimeType -> "bmp"
        "svg" in normalizedMimeType -> "svg"
        else -> detectImageExtension(bytes)
    }
}

private fun resolveImageMimeType(
    declaredMimeType: String,
    bytes: ByteArray,
): String {
    val normalizedMimeType = declaredMimeType.trim().lowercase()
    if (normalizedMimeType.startsWith("image/")) {
        return normalizedMimeType
    }
    return when (detectImageExtension(bytes)) {
        "jpg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        else -> "image/png"
    }
}

private fun detectImageExtension(bytes: ByteArray): String {
    if (bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte()
    ) {
        return "png"
    }
    if (bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte()
    ) {
        return "jpg"
    }
    if (bytes.size >= 6) {
        val header = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
        if (header == "GIF87a" || header == "GIF89a") {
            return "gif"
        }
    }
    if (bytes.size >= 12) {
        val riff = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
        val webp = bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII)
        if (riff == "RIFF" && webp == "WEBP") {
            return "webp"
        }
    }
    if (bytes.size >= 2 &&
        bytes[0] == 0x42.toByte() &&
        bytes[1] == 0x4D.toByte()
    ) {
        return "bmp"
    }
    val textHeader = bytes.take(128).toByteArray()
        .toString(Charsets.UTF_8)
        .trimStart()
        .lowercase()
    if (textHeader.startsWith("<svg")) {
        return "svg"
    }
    return "png"
}

private fun buildUniqueTargetFile(
    directory: File,
    fileName: String,
): File {
    val baseName = fileName.substringBeforeLast('.', fileName)
    val extension = fileName.substringAfterLast('.', "")
    var index = 0
    while (true) {
        val candidateName = if (index == 0) {
            fileName
        } else if (extension.isBlank()) {
            "${baseName}_$index"
        } else {
            "${baseName}_$index.$extension"
        }
        val candidate = File(directory, candidateName)
        if (!candidate.exists()) {
            return candidate
        }
        index += 1
    }
}

private fun resolveRemoteFileName(url: String): String {
    val lastPathSegment = runCatching { url.toUri().lastPathSegment }.getOrNull().orEmpty()
    return lastPathSegment.substringAfterLast('/').trim()
}

private fun guessMimeTypeFromFileName(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        else -> ""
    }
}

private fun Throwable.toUserMessage(): String {
    return message?.trim().orEmpty().ifBlank { "保存图片失败" }
}
