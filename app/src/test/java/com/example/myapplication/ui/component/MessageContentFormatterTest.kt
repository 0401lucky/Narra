package com.example.myapplication.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContentFormatterTest {
    @Test
    fun normalizeAssistantMarkdownForDisplay_insertsBlankLinesBetweenPlainLines() {
        val input = """
            第一段
            第二段
            第三段
        """.trimIndent()

        val actual = normalizeAssistantMarkdownForDisplay(input)

        assertEquals("第一段\n\n第二段\n\n第三段", actual)
    }

    @Test
    fun normalizeAssistantMarkdownForDisplay_keepsStructuredMarkdownUntouched() {
        val input = """
            # 标题
            - 要点一
            - 要点二
        """.trimIndent()

        val actual = normalizeAssistantMarkdownForDisplay(input)

        assertEquals(input, actual)
    }

    @Test
    fun normalizeAssistantMarkdownForDisplay_keepsExistingParagraphBreaks() {
        val input = """
            第一段

            第二段
        """.trimIndent()

        val actual = normalizeAssistantMarkdownForDisplay(input)

        assertEquals(input, actual)
    }

    @Test
    fun normalizeAssistantMarkdownForDisplay_wrapsCommandLinesIntoCodeFence() {
        val input = """
            建议先检查版本：
            powershell
            ${'$'}PSVersionTable.PSVersion
            winget list --id Microsoft.PowerShell --upgrade-available
        """.trimIndent()

        val actual = normalizeAssistantMarkdownForDisplay(input)

        assertEquals(
            """
            建议先检查版本：
            ```powershell
            powershell
            ${'$'}PSVersionTable.PSVersion
            winget list --id Microsoft.PowerShell --upgrade-available
            ```
            """.trimIndent(),
            actual,
        )
    }

    @Test
    fun normalizeAssistantMarkdownForDisplay_wrapsContinuationLinesIntoCodeFence() {
        val input = """
            依次运行下面命令：
            winget list --id
            Microsoft.PowerShell
            --upgrade-available
        """.trimIndent()

        val actual = normalizeAssistantMarkdownForDisplay(input)

        assertEquals(
            """
            依次运行下面命令：
            ```powershell
            winget list --id
            Microsoft.PowerShell
            --upgrade-available
            ```
            """.trimIndent(),
            actual,
        )
    }

    @Test
    fun normalizeAssistantMarkdownForDisplay_wrapsWholeLineInlineCommandsIntoCodeFence() {
        val input = """
            先检查版本：
            `powershell`
            `${'$'}PSVersionTable.PSVersion`
        """.trimIndent()

        val actual = normalizeAssistantMarkdownForDisplay(input)

        assertEquals(
            """
            先检查版本：
            ```powershell
            powershell
            ${'$'}PSVersionTable.PSVersion
            ```
            """.trimIndent(),
            actual,
        )
    }

    @Test
    fun extractAssistantVisualContent_extractsMarkdownImagesAndKeepsText() {
        val input = """
            先看图

            ![猫](https://example.com/cat.png)

            再继续说明
        """.trimIndent()

        val actual = extractAssistantVisualContent(input)

        assertEquals("先看图\n\n再继续说明", actual.text)
        assertEquals(listOf("https://example.com/cat.png"), actual.imageSources)
    }

    @Test
    fun extractAssistantVisualContent_extractsHtmlAndDataImages() {
        val input = """
            <img src="https://example.com/dog.webp" alt="dog" />

            data:image/png;base64,ZmFrZQ==
        """.trimIndent()

        val actual = extractAssistantVisualContent(input)

        assertTrue(actual.text.isBlank())
        assertEquals(
            listOf(
                "https://example.com/dog.webp",
                "data:image/png;base64,ZmFrZQ==",
            ),
            actual.imageSources,
        )
    }

    @Test
    fun extractAssistantVisualContent_extractsSingleSignedUrlWithoutSuffix() {
        val input = "https://cdn.example.com/generated/no-extension-signed?token=abc123"

        val actual = extractAssistantVisualContent(input)

        assertTrue(actual.text.isBlank())
        assertEquals(listOf(input), actual.imageSources)
    }

    @Test
    fun extractAssistantVisualContent_extractsContentSchemeImage() {
        val input = "content://generated/image/42.png"

        val actual = extractAssistantVisualContent(input)

        assertTrue(actual.text.isBlank())
        assertEquals(listOf(input), actual.imageSources)
    }

    @Test
    fun extractAssistantVisualContent_keepsPlainWebUrlAsText() {
        val input = "https://example.com/docs/getting-started"

        val actual = extractAssistantVisualContent(input)

        assertEquals(input, actual.text)
        assertTrue(actual.imageSources.isEmpty())
    }
}
