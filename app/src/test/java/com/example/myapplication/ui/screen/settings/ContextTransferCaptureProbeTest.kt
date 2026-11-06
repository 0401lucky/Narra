package com.example.myapplication.ui.screen.settings

import android.graphics.Bitmap
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.*
import com.example.myapplication.ui.screenshot.ContextTransferRenderTest
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.viewmodel.ContextTransferSection
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class ContextTransferCaptureProbeTest : ContextTransferRenderTest() {
    @Test
    fun sourceDialog_isRealAndCancelDoesNotLaunchChooser() {
        renderTransfer(ScreenshotCases.require("E06_transfer-source-dialog-light"))
        assertThrows(AssertionError::class.java) { dialogRoot(SourceTitle) }
        openSource()
        probeRenderedRoot("transfer-source-dialog", dialogRoot(SourceTitle))
        windowNode(dialogMatcher(SourceTitle), hasText("取消") and hasClickAction()).performClick()
        assertThrows(AssertionError::class.java) { dialogRoot(SourceTitle) }
        assertCaptureIsolation()
    }

    @Test
    fun nullResults_fromThreeRealLaunchers_haveNoIoOrCallbacks() {
        renderTransfer(ScreenshotCases.require("E06_transfer-matcha-light"), preview = true)
        revealSection("预设包")
        sectionButton("预设包", "导出").performClick()
        assertTrue(registry.launches.last().contract is ActivityResultContracts.CreateDocument)
        assertEquals("narra-preset-pack.json", registry.launches.last().input)
        dispatch(null)
        sectionButton("预设包", "导入").performClick()
        assertTrue(registry.launches.last().contract is ActivityResultContracts.OpenDocument)
        assertArrayEquals(
            arrayOf("application/json", "text/plain"),
            registry.launches.last().input as Array<*>
        )
        dispatch(null)
        openSource("世界书")
        windowNode(
            dialogMatcher(SourceTitle),
            hasText("从相册（PNG）") and hasClickAction()
        ).performClick()
        assertTrue(registry.launches.last().contract is ActivityResultContracts.PickVisualMedia)
        assertEquals(
            ActivityResultContracts.PickVisualMedia.ImageOnly,
            (registry.launches.last().input as PickVisualMediaRequest).mediaType
        )
        dispatch(null)
        assertNoExternalActivity()
        assertEquals(3, registry.launches.size)
        assertEquals(0, inputOpens.get())
        assertEquals(0, outputOpens.get())
        assertEquals(0, provider.queries.get())
        assertTrue(events.isEmpty())
        assertTrue(payloads.isEmpty())
        assertEquals(Preview, state.value.importPreview)
    }

    @Test
    fun documentResult_readsOnlyMemoryUri_stripsBom_andKeepsSection() {
        val uri = registerInput(
            "fixture.json",
            ("\uFEFF" + ExportJson).toByteArray(Charsets.UTF_8),
            "application/json"
        )
        renderTransfer(ScreenshotCases.require("E06_transfer-matcha-light"))
        revealSection("记忆档案包")
        sectionButton("记忆档案包", "导入").performClick()
        dispatch(uri)
        composeRule.waitUntil(5_000) { payloads.size == 1 }
        assertEquals(ContextTransferSection.MEMORY, payloads.single().first)
        val payload = payloads.single().second
        assertEquals("fixture.json", payload.fileName)
        assertEquals("application/json", payload.mimeType)
        assertEquals(ExportJson, payload.textContent)
        assertNull(payload.binaryContent)
        assertEquals(1, inputOpens.get())
        assertEquals(1, inputCloses.get())
        assertEquals(1, provider.queries.get())
        assertNoExternalActivity()
    }

    @Test
    fun photoResult_readsConstructedPngFromMemory_onlyAfterResult() {
        val png = ByteArrayOutputStream().also { output ->
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
                compress(Bitmap.CompressFormat.PNG, 100, output)
                recycle()
            }
        }.toByteArray()
        val uri = registerInput("fixture.png", png, "image/png")
        renderTransfer(ScreenshotCases.require("E06_transfer-matcha-light"))
        openSource("世界书")
        windowNode(
            dialogMatcher(SourceTitle),
            hasText("从相册（PNG）") and hasClickAction()
        ).performClick()
        assertEquals(0, inputOpens.get())
        dispatch(uri)
        composeRule.waitUntil(5_000) { payloads.size == 1 }
        assertEquals(ContextTransferSection.WORLD_BOOK, payloads.single().first)
        assertArrayEquals(png, payloads.single().second.binaryContent)
        assertNull(payloads.single().second.textContent)
        assertEquals(1, inputCloses.get())
        assertNoExternalActivity()
    }

    @Test
    fun exportResult_writesOnlyRegisteredMemoryStream_andClosesIt() {
        val uri = registerOutput("export.json")
        renderTransfer(ScreenshotCases.require("E06_transfer-matcha-light"))
        revealSection("预设包")
        sectionButton("预设包", "导出").performClick()
        assertTrue(events.isEmpty())
        dispatch(uri)
        composeRule.waitUntil(5_000) { outputCloses.get() == 1 }
        assertEquals(listOf("export:PRESETS"), events)
        assertEquals(ExportJson, exported.toString(Charsets.UTF_8.name()))
        assertEquals(1, outputOpens.get())
        assertNoExternalActivity()
    }
}
