package com.example.myapplication.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatImageGallerySaverTest {
    @Test
    fun save_readsLocalFileAndWritesToGallery() = runBlocking {
        val reader = FakeChatImageSourceReader(
            fileResult = LoadedChatImage(
                bytes = byteArrayOf(0x01, 0x02),
                mimeType = "image/png",
                fileName = "from-file.png",
            ),
        )
        val writer = FakeChatImageGalleryWriter()
        val saver = ChatImageGallerySaver(
            sourceReader = reader,
            galleryWriter = writer,
            fileNamePrefixProvider = { "fallback-name" },
        )

        val result = saver.save("C:\\images\\from-file.png")

        assertTrue(result is SaveImageResult.Success)
        assertEquals(listOf("C:\\images\\from-file.png"), reader.fileRequests)
        assertEquals("from-file.png", writer.lastSavedImage?.fileName)
        assertEquals("image/png", writer.lastSavedImage?.mimeType)
    }

    @Test
    fun save_readsContentUriAndFallsBackToGeneratedFileName() = runBlocking {
        val reader = FakeChatImageSourceReader(
            contentResult = LoadedChatImage(
                bytes = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50),
                mimeType = "image/webp",
                fileName = "",
            ),
        )
        val writer = FakeChatImageGalleryWriter()
        val saver = ChatImageGallerySaver(
            sourceReader = reader,
            galleryWriter = writer,
            fileNamePrefixProvider = { "gallery-image" },
        )

        val result = saver.save("content://media/external/images/10")

        assertTrue(result is SaveImageResult.Success)
        assertEquals(listOf("content://media/external/images/10"), reader.contentRequests)
        assertEquals("gallery-image.webp", writer.lastSavedImage?.fileName)
    }

    @Test
    fun save_decodesDataImageAndKeepsDisplayName() = runBlocking {
        val writer = FakeChatImageGalleryWriter()
        val saver = ChatImageGallerySaver(
            sourceReader = FakeChatImageSourceReader(),
            galleryWriter = writer,
            fileNamePrefixProvider = { "ignored" },
        )

        val result = saver.save(
            source = "data:image/png;base64,AQID",
            displayName = "preview-image",
        )

        assertTrue(result is SaveImageResult.Success)
        assertEquals("preview-image.png", writer.lastSavedImage?.fileName)
        assertEquals("image/png", writer.lastSavedImage?.mimeType)
        assertTrue(
            writer.lastSavedImage?.bytes?.contentEquals(byteArrayOf(0x01, 0x02, 0x03)) == true,
        )
    }

    @Test
    fun save_downloadsRemoteImageBeforeWriting() = runBlocking {
        val reader = FakeChatImageSourceReader(
            remoteResult = LoadedChatImage(
                bytes = byteArrayOf(0x42, 0x4D),
                mimeType = "image/bmp",
                fileName = "remote.bmp",
            ),
        )
        val writer = FakeChatImageGalleryWriter()
        val saver = ChatImageGallerySaver(
            sourceReader = reader,
            galleryWriter = writer,
            fileNamePrefixProvider = { "remote-fallback" },
        )

        val result = saver.save("https://cdn.example.com/generated/remote-image")

        assertTrue(result is SaveImageResult.Success)
        assertEquals(
            listOf("https://cdn.example.com/generated/remote-image"),
            reader.remoteRequests,
        )
        assertEquals("remote.bmp", writer.lastSavedImage?.fileName)
        assertEquals("image/bmp", writer.lastSavedImage?.mimeType)
    }

    @Test
    fun save_returnsFriendlyFailureWhenRemoteDownloadFails() = runBlocking {
        val saver = ChatImageGallerySaver(
            sourceReader = FakeChatImageSourceReader(
                remoteFailure = IllegalStateException("网络超时"),
            ),
            galleryWriter = FakeChatImageGalleryWriter(),
            fileNamePrefixProvider = { "unused" },
        )

        val result = saver.save("https://cdn.example.com/generated/remote-image")

        assertEquals(
            SaveImageResult.Failure("下载图片失败：网络超时"),
            result,
        )
    }

    @Test
    fun save_returnsWriterFailureWhenGalleryWriteFails() = runBlocking {
        val saver = ChatImageGallerySaver(
            sourceReader = FakeChatImageSourceReader(
                fileResult = LoadedChatImage(
                    bytes = byteArrayOf(0x01, 0x02),
                    mimeType = "image/png",
                    fileName = "from-file.png",
                ),
            ),
            galleryWriter = FakeChatImageGalleryWriter(
                result = SaveImageResult.Failure("没有存储权限，无法保存到相册"),
            ),
            fileNamePrefixProvider = { "unused" },
        )

        val result = saver.save("C:\\images\\from-file.png")

        assertEquals(
            SaveImageResult.Failure("没有存储权限，无法保存到相册"),
            result,
        )
    }
}

private class FakeChatImageSourceReader(
    private val fileResult: LoadedChatImage? = null,
    private val contentResult: LoadedChatImage? = null,
    private val remoteResult: LoadedChatImage? = null,
    private val remoteFailure: Throwable? = null,
) : ChatImageSourceReader {
    val fileRequests = mutableListOf<String>()
    val contentRequests = mutableListOf<String>()
    val remoteRequests = mutableListOf<String>()

    override suspend fun readFile(path: String): LoadedChatImage {
        fileRequests += path
        return fileResult ?: error("未配置本地文件返回值")
    }

    override suspend fun readContentUri(uri: String): LoadedChatImage {
        contentRequests += uri
        return contentResult ?: error("未配置 content uri 返回值")
    }

    override suspend fun download(url: String): LoadedChatImage {
        remoteRequests += url
        remoteFailure?.let { throw it }
        return remoteResult ?: error("未配置远程下载返回值")
    }
}

private class FakeChatImageGalleryWriter(
    private val result: SaveImageResult = SaveImageResult.Success("saved.png"),
) : ChatImageGalleryWriter {
    var lastSavedImage: LoadedChatImage? = null

    override suspend fun write(image: LoadedChatImage): SaveImageResult {
        lastSavedImage = image
        return result
    }
}
