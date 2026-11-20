package com.example.myapplication.data.repository.roleplay

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

class AndroidCompanionTextDocumentWriter(
    private val resolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CompanionTextDocumentWriter {
    override suspend fun write(documentUri: String, content: ByteArray) =
        withContext(ioDispatcher) {
            val uri = Uri.parse(documentUri)
            require(uri.scheme == "content") { "请选择系统文件保存位置" }
            val stream =
                resolver.openOutputStream(uri, "wt") ?: throw IOException("无法打开所选文件")
            stream.use { output ->
                var offset = 0
                while (offset < content.size) {
                    ensureActive()
                    val size = minOf(8_192, content.size - offset)
                    output.write(content, offset, size)
                    offset += size
                }
                output.flush()
            }
            ensureActive()
        }
}
