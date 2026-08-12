package com.example.myapplication.system.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessExitInfoSourceTest {
    @Test
    fun query_api29ReturnsUnsupportedWithoutCallingPlatformReader() {
        var readerCalls = 0
        val source = AndroidProcessExitInfoSource(
            sdkInt = 29,
            api30Reader = Api30ProcessExitInfoReader {
                readerCalls++
                emptyList()
            },
        )

        val result = source.query(maxRecords = 20)

        assertSame(ProcessExitQueryResult.Unsupported, result)
        assertEquals(HistoricalExitQuerySupport.UNSUPPORTED, source.supportStatus)
        assertEquals(0, readerCalls)
    }

    @Test
    fun query_api30ReturnsOnlyReaderProvidedAllowlistSnapshot() {
        val expected = RawProcessExitRecord(
            reasonCode = ProcessExitReasonCodes.CRASH,
            timestampEpochMillis = 100L,
            importance = 200,
            status = 1,
            pssKb = 2_048L,
            rssKb = 4_096L,
        )
        var requestedCount = 0
        val source = AndroidProcessExitInfoSource(
            sdkInt = 30,
            api30Reader = Api30ProcessExitInfoReader { maxRecords ->
                requestedCount = maxRecords
                listOf(expected)
            },
        )

        val result = source.query(maxRecords = 12)

        assertEquals(HistoricalExitQuerySupport.SUPPORTED, source.supportStatus)
        assertEquals(12, requestedCount)
        assertEquals(ProcessExitQueryResult.Success(listOf(expected)), result)
    }

    @Test
    fun api30ReaderForSdk_api29DoesNotConstructSupportedReader() {
        var supportedReaderConstructions = 0

        val reader = api30ReaderForSdk(sdkInt = 29) {
            supportedReaderConstructions++
            Api30ProcessExitInfoReader { error("旧系统不应调用 API30 reader") }
        }

        assertEquals(0, supportedReaderConstructions)
        assertTrue(reader.read(20).isEmpty())
    }
}
