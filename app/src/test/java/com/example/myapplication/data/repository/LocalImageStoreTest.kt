package com.example.myapplication.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalImageStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun resolveImageExtension_returnsJpgForBlank() {
        assertEquals("jpg", resolveImageExtension(null))
        assertEquals("jpg", resolveImageExtension(""))
        assertEquals("jpg", resolveImageExtension("   "))
    }

    @Test
    fun resolveImageExtension_mapsCommonMimeTypes() {
        assertEquals("png", resolveImageExtension("image/png"))
        assertEquals("jpg", resolveImageExtension("image/jpeg"))
        assertEquals("webp", resolveImageExtension("image/webp"))
        assertEquals("gif", resolveImageExtension("image/gif"))
        assertEquals("bmp", resolveImageExtension("image/bmp"))
        assertEquals("heic", resolveImageExtension("image/heic"))
        assertEquals("heic", resolveImageExtension("image/heif"))
    }

    @Test
    fun resolveImageExtension_fallsBackToJpgForUnknown() {
        assertEquals("jpg", resolveImageExtension("application/octet-stream"))
        assertEquals("jpg", resolveImageExtension("image/x-custom"))
    }

    @Test
    fun sanitizeImageScope_stripsUnsafeCharacters() {
        assertEquals("avatar", sanitizeImageScope("avatar"))
        assertEquals("scenario_background", sanitizeImageScope("scenario background"))
        assertEquals("scenario_user", sanitizeImageScope("scenario/user"))
        assertEquals("general", sanitizeImageScope(""))
        assertEquals("general", sanitizeImageScope("///"))
    }

    @Test
    fun normalizeImagePath_acceptsAbsolutePath() {
        assertEquals("/data/user/0/app/files/img.jpg", normalizeImagePath("/data/user/0/app/files/img.jpg"))
    }

    @Test
    fun normalizeImagePath_stripsFileScheme() {
        assertEquals("/storage/image.png", normalizeImagePath("file:///storage/image.png"))
        assertEquals("/storage/image.png", normalizeImagePath("file:/storage/image.png"))
    }

    @Test
    fun normalizeImagePath_rejectsNonFileUri() {
        assertNull(normalizeImagePath(null))
        assertNull(normalizeImagePath(""))
        assertNull(normalizeImagePath("content://media/external/images/1"))
        assertNull(normalizeImagePath("relative/path.jpg"))
    }

    @Test
    fun deleteIfInsideBaseDir_removesFileUnderBase() {
        val base = tempFolder.newFolder("user_images")
        val scopeDir = File(base, "avatar").apply { mkdirs() }
        val file = File(scopeDir, "a.jpg").apply { writeText("x") }

        val deleted = deleteIfInsideBaseDir(base, file.absolutePath)

        assertTrue(deleted)
        assertFalse(file.exists())
    }

    @Test
    fun deleteIfInsideBaseDir_acceptsFileUri() {
        val base = tempFolder.newFolder("user_images")
        val file = File(base, "a.jpg").apply { writeText("x") }

        val deleted = deleteIfInsideBaseDir(base, "file://" + file.absolutePath)

        assertTrue(deleted)
        assertFalse(file.exists())
    }

    @Test
    fun deleteIfInsideBaseDir_ignoresPathOutsideBase() {
        val base = tempFolder.newFolder("user_images")
        val outside = tempFolder.newFile("outside.jpg").apply { writeText("keep") }

        val deleted = deleteIfInsideBaseDir(base, outside.absolutePath)

        assertFalse(deleted)
        assertTrue(outside.exists())
    }

    @Test
    fun deleteIfInsideBaseDir_ignoresContentUriOrBlank() {
        val base = tempFolder.newFolder("user_images")
        assertFalse(deleteIfInsideBaseDir(base, null))
        assertFalse(deleteIfInsideBaseDir(base, ""))
        assertFalse(deleteIfInsideBaseDir(base, "content://media/external/images/1"))
    }

    @Test
    fun deleteIfInsideBaseDir_returnsFalseWhenFileMissing() {
        val base = tempFolder.newFolder("user_images")
        val phantom = File(base, "does-not-exist.jpg")
        assertFalse(deleteIfInsideBaseDir(base, phantom.absolutePath))
    }
}
