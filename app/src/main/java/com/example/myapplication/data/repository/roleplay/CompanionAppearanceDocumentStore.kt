package com.example.myapplication.data.repository.roleplay

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** SAF 只负责文档传输；装扮内容与恢复授权由资料仓储校验。 */
class CompanionAppearanceDocumentStore(private val resolver: ContentResolver) {
    suspend fun read(documentUri: String): String = withContext(Dispatchers.IO) {
        ensureActive()
        val uri = Uri.parse(documentUri)
        require(uri.scheme == "content") { "请选择系统文件中的装扮包" }
        val raw = resolver.openInputStream(uri)?.use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                check(bytes.size() + read <= MAX_DOCUMENT_BYTES) { "装扮文件超过允许大小" }
                bytes.write(buffer, 0, read)
            }
            bytes.toString(Charsets.UTF_8.name())
        } ?: error("无法读取所选装扮文件")
        ensureActive()
        raw
    }

    suspend fun write(documentUri: String, json: String): Unit = withContext(Dispatchers.IO) {
        ensureActive()
        val uri = Uri.parse(documentUri)
        require(uri.scheme == "content") { "请选择系统文件保存位置" }
        val bytes = json.toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_DOCUMENT_BYTES) { "装扮文件超过允许大小" }
        val output = resolver.openOutputStream(uri, "wt")
            ?: error("无法写入所选装扮文件")
        output.use {
            var offset = 0
            while (offset < bytes.size) {
                ensureActive()
                val size = minOf(8 * 1024, bytes.size - offset)
                it.write(bytes, offset, size)
                offset += size
            }
            ensureActive()
            it.flush()
        }
        ensureActive()
    }

    companion object {
        const val MAX_DOCUMENT_BYTES = 16 * 1024 * 1024
    }
}
