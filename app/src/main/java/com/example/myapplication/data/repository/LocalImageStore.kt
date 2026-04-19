package com.example.myapplication.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 把系统相册/文件管理器选中的 content:// URI 复制到 app 私有存储。
 * 统一返回本地 absolute path，消除 Photo Picker / SAF URI 在进程重启后失效的隐患。
 * `scope` 作为 `filesDir/user_images/<scope>/` 子目录名，便于按用途隔离与清理。
 */
class LocalImageStore(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun copyToAppStorage(uri: Uri, scope: String): String? = withContext(dispatcher) {
        val resolver = context.contentResolver
        val extension = resolveImageExtension(runCatching { resolver.getType(uri) }.getOrNull())
        val dir = File(context.filesDir, "$USER_IMAGE_ROOT/${sanitizeImageScope(scope)}")
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) {
            return@withContext null
        }
        val target = File(dir, "${UUID.randomUUID()}.$extension")
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
        }.getOrElse {
            target.delete()
            false
        }
        if (copied) target.absolutePath else null
    }

    fun deleteIfLocal(path: String?): Boolean {
        val baseDir = File(context.filesDir, USER_IMAGE_ROOT)
        return deleteIfInsideBaseDir(baseDir, path)
    }

    suspend fun deleteIfLocalAsync(path: String?): Boolean = withContext(dispatcher) {
        deleteIfLocal(path)
    }
}

internal const val USER_IMAGE_ROOT = "user_images"
private const val DEFAULT_IMAGE_EXTENSION = "jpg"
private const val DEFAULT_SCOPE = "general"

internal fun resolveImageExtension(mimeType: String?): String {
    val normalized = mimeType?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return DEFAULT_IMAGE_EXTENSION
    return when {
        normalized.contains("png") -> "png"
        normalized.contains("webp") -> "webp"
        normalized.contains("gif") -> "gif"
        normalized.contains("bmp") -> "bmp"
        normalized.contains("heic") || normalized.contains("heif") -> "heic"
        normalized.contains("jpeg") || normalized.contains("jpg") -> "jpg"
        else -> DEFAULT_IMAGE_EXTENSION
    }
}

internal fun sanitizeImageScope(scope: String): String {
    val safe = scope.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_', '-')
    return safe.ifBlank { DEFAULT_SCOPE }
}

internal fun normalizeImagePath(path: String?): String? {
    if (path.isNullOrBlank()) return null
    val stripped = when {
        path.startsWith("file://") -> path.removePrefix("file://")
        path.startsWith("file:") -> path.removePrefix("file:")
        else -> path
    }
    if (stripped.isBlank()) return null
    val looksAbsolute = stripped.startsWith("/") || File(stripped).isAbsolute
    if (!looksAbsolute) return null
    return stripped
}

internal fun deleteIfInsideBaseDir(baseDir: File, path: String?): Boolean {
    val absolute = normalizeImagePath(path) ?: return false
    val target = File(absolute)
    val baseCanonical = runCatching { baseDir.canonicalPath }.getOrNull() ?: return false
    val targetCanonical = runCatching { target.canonicalPath }.getOrNull() ?: return false
    val prefixed = targetCanonical == baseCanonical ||
        targetCanonical.startsWith(baseCanonical + File.separator)
    if (!prefixed) return false
    return runCatching { target.delete() }.getOrDefault(false)
}
