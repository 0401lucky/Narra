package com.example.myapplication.data.repository.roleplay

import android.app.Application
import android.net.Uri
import com.example.myapplication.model.*
import com.example.myapplication.testutil.FakeCompanionHistoryRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionTextExportTest {
    @Test
    fun fullRepositoryExport_keepsUnloadedRawTimeSpeakerAndTypedControl() = runBlocking<Unit> {
        val repository = FakeCompanionHistoryRepository()
        val raw =
            "  <narration>" + "完整舞台原文🙂 é <literal> &#65;  ".repeat(160) + "</narration>\n<observe><detail>原始观测</detail></observe>  "
        repository.data = repository.data.map { record ->
            if (record.message.id == "history-000") record.copy(
                message = record.message.copy(
                    content = raw,
                    speakerName = "当时署名"
                )
            )
            else if (record.message.id == "history-001") record.copy(
                message = record.message.copy(
                    content = COMPANION_CONTINUE_DISPLAY_TEXT
                ), turnKind = CompanionTurnKind.CONTINUE
            )
            else record
        }
        val query = CompanionHistoryQuery()
        val window = repository.page(repository.scope, query, limit = 50)
        assertEquals(50, window.records.size)
        assertFalse(window.records.any { it.message.id == "history-000" })
        val output = ByteArrayOutputStream()
        val exporter = CompanionTextExporter(
            repository,
            CompanionTextDocumentWriter { _, bytes -> output.write(bytes) })
        val result = exporter.export(repository.scope, query, "content://synthetic/export.txt")
        assertEquals(CompanionTextExportResult.Saved(125), result)
        assertEquals(1, repository.readAllCalls)
        val bytes = output.toByteArray()
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            bytes.take(3).toByteArray()
        )
        val text = String(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)
        assertTrue(text.contains(raw))
        assertTrue(text.contains("USER · 当时署名"))
        assertTrue(text.contains("1970-01-01 00:00:01.000 Z"))
        assertTrue(text.contains("类型=CONTINUE"))
        assertTrue(text.contains(COMPANION_CONTINUE_DISPLAY_TEXT))
        assertEquals(125, Regex(" · source=history-[0-9]{3}@1\\n").findAll(text).count())
    }

    @Test
    fun cancelledSaf_isZeroReadZeroWriteAndNoSuccess() = runBlocking<Unit> {
        val repository = FakeCompanionHistoryRepository()
        var writes = 0
        val exporter =
            CompanionTextExporter(repository, CompanionTextDocumentWriter { _, _ -> writes++ })
        assertEquals(
            CompanionTextExportResult.Cancelled,
            exporter.export(repository.scope, CompanionHistoryQuery(), null)
        )
        assertEquals(0, writes)
        assertEquals(0, repository.readAllCalls)
    }

    @Test
    fun successfulReadThenRevokedScope_doesNotWriteItsFrozenOriginals() = runBlocking<Unit> {
        val repository = FakeCompanionHistoryRepository().apply {
            afterRead = { valid = false }
        }
        val originals = repository.data
        var writes = 0
        val exporter =
            CompanionTextExporter(repository, CompanionTextDocumentWriter { _, _ -> writes++ })

        val result = exporter.export(
            repository.scope, CompanionHistoryQuery(), "content://synthetic/revoked.txt"
        )

        assertEquals(1, repository.readAllCalls)
        assertEquals(0, writes)
        assertTrue(result is CompanionTextExportResult.Failed)
        assertEquals(originals, repository.data)
    }

    @Test
    fun safWriteAndCloseFailures_neverReportSavedAndCloseTheStream() = runBlocking<Unit> {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        for (failClose in listOf(false, true)) {
            val uri = Uri.parse("content://synthetic/${if (failClose) "close" else "write"}.txt")
            var closes = 0
            shadowOf(resolver).registerOutputStreamSupplier(uri) {
                object : OutputStream() {
                    override fun write(value: Int) {
                        if (!failClose) throw IOException("/data/user/0/fake/write")
                    }

                    override fun close() {
                        closes++; if (failClose) throw IOException("/data/user/0/fake/close")
                    }
                }
            }
            val repository = FakeCompanionHistoryRepository()
            val exporter = CompanionTextExporter(
                repository,
                AndroidCompanionTextDocumentWriter(resolver, Dispatchers.Unconfined)
            )
            val result = exporter.export(repository.scope, CompanionHistoryQuery(), uri.toString())
            assertTrue(result is CompanionTextExportResult.Failed)
            assertFalse((result as CompanionTextExportResult.Failed).message.contains("/data/user"))
            assertEquals(1, closes)
        }
    }

    @Test
    fun safNullStreamAndPermissionFailure_neverReportSaved() = runBlocking<Unit> {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val missing = Uri.parse("content://synthetic/missing.txt")
        val denied = Uri.parse("content://synthetic/denied.txt")
        shadowOf(resolver).registerOutputStreamSupplier(missing) { null }
        shadowOf(resolver).registerOutputStreamSupplier(denied) { throw SecurityException("/data/private/fake") }
        val repository = FakeCompanionHistoryRepository()
        val exporter = CompanionTextExporter(
            repository,
            AndroidCompanionTextDocumentWriter(resolver, Dispatchers.Unconfined)
        )
        for (uri in listOf(missing, denied)) {
            assertTrue(
                exporter.export(
                    repository.scope,
                    CompanionHistoryQuery(),
                    uri.toString()
                ) is CompanionTextExportResult.Failed
            )
        }
    }

    @Test
    fun coroutineCancellation_propagatesInsteadOfReportingSaved() = runBlocking<Unit> {
        val repository = FakeCompanionHistoryRepository()
        val exporter = CompanionTextExporter(
            repository,
            CompanionTextDocumentWriter { _, _ -> throw CancellationException("合成取消") })
        val failure = runCatching {
            exporter.export(
                repository.scope,
                CompanionHistoryQuery(),
                "content://synthetic/cancel"
            )
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test
    fun filename_isLegalAndDoesNotSplitSurrogates() {
        val name = CompanionTextExporter.fileName(
            "../CON:角色\\照片\n" + "🙂".repeat(100),
            CompanionHistoryQuery(month = "2026-11")
        )
        assertFalse(name.any { it in "<>:\"/\\|?*\n\r" })
        assertTrue(name.startsWith("陪伴记录-"))
        assertTrue(name.endsWith("-2026-11.txt"))
        val longEmojiName = CompanionTextExporter.fileName(
            "🙂".repeat(100),
            CompanionHistoryQuery(date = "2026-11-19")
        )
        assertTrue(longEmojiName.toByteArray(Charsets.UTF_8).size <= 255)
        assertTrue(longEmojiName.endsWith("-2026-11-19.txt"))
        for (index in name.indices) {
            if (name[index].isHighSurrogate()) assertTrue(index + 1 < name.length && name[index + 1].isLowSurrogate())
            if (name[index].isLowSurrogate()) assertTrue(index > 0 && name[index - 1].isHighSurrogate())
        }
        for (index in longEmojiName.indices) {
            if (longEmojiName[index].isHighSurrogate()) assertTrue(index + 1 < longEmojiName.length && longEmojiName[index + 1].isLowSurrogate())
            if (longEmojiName[index].isLowSurrogate()) assertTrue(index > 0 && longEmojiName[index - 1].isHighSurrogate())
        }
    }
}
