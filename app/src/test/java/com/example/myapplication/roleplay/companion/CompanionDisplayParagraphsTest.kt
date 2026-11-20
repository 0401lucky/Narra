package com.example.myapplication.roleplay.companion

import androidx.compose.ui.graphics.Color
import com.example.myapplication.ui.component.roleplay.buildCharacterDialogueAnnotatedString
import com.example.myapplication.ui.component.roleplay.buildQuotedDialogueAnnotatedString
import com.example.myapplication.ui.component.roleplay.toLongformParagraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionDisplayParagraphsTest {
    @Test
    fun annotatedParsedWhitespace_preservesCharactersWhileOldDefaultStillOmitsBlank() {
        val whitespace = " ".repeat(300)
        assertEquals(
            "",
            buildQuotedDialogueAnnotatedString(whitespace, Color.Black, Color.Blue).text
        )
        assertEquals(
            "",
            buildCharacterDialogueAnnotatedString(whitespace, Color.Black, Color.Blue).text
        )
        assertEquals(
            "",
            buildQuotedDialogueAnnotatedString(whitespace, Color.Black, Color.Blue, false).text
        )
        assertEquals(
            "",
            buildCharacterDialogueAnnotatedString(whitespace, Color.Black, Color.Blue, false).text
        )
        assertEquals(
            whitespace,
            buildQuotedDialogueAnnotatedString(whitespace, Color.Black, Color.Blue, true).text
        )
        assertEquals(
            whitespace,
            buildCharacterDialogueAnnotatedString(whitespace, Color.Black, Color.Blue, true).text
        )
        val body = "前" + whitespace + "后".repeat(100)
        val parts = body.toLongformParagraphs(parsedCompanion = true)
        assertTrue(
            "合法已解析正文须包含真实全空白中间段",
            parts.any { it.isNotEmpty() && it.isBlank() })
        assertEquals(body, parts.joinToString("") {
            buildQuotedDialogueAnnotatedString(it, Color.Black, Color.Blue, true).text
        })
        assertEquals(body, parts.joinToString("") {
            buildCharacterDialogueAnnotatedString(it, Color.Black, Color.Blue, true).text
        })
    }

    @Test
    fun parsedTagsAndEntityLookingText_arePreservedWithoutAnotherDecode() {
        val text = "字面 <narration>仍应显示</narration> 与 &#65;、&amp;#65;。"
        assertEquals(listOf(text), text.toLongformParagraphs(parsedCompanion = true))
    }

    @Test
    fun oldDefaultAndExplicitFalse_keepLegacyMarkupCleanup() {
        val raw = "<narration>旧路径正文。</narration>"
        assertEquals(listOf("旧路径正文。"), raw.toLongformParagraphs())
        assertEquals(raw.toLongformParagraphs(), raw.toLongformParagraphs(parsedCompanion = false))
        assertEquals(listOf(raw), raw.toLongformParagraphs(parsedCompanion = true))
    }

    @Test
    fun emptyAndWhitespace_onlySplitWithoutDroppingParsedCharacters() {
        assertEquals(emptyList<String>(), CompanionDisplayParagraphs.split(""))
        val text = "  \r\n\n\t  " + " ".repeat(160)
        val parts = CompanionDisplayParagraphs.split(text)
        assertEquals(text, parts.joinToString(""))
        assertTrue(parts.all { it.isNotEmpty() })
        assertIndependentUtf16(parts)
        assertTrue(parts.none { it.endsWith('\r') })
    }

    @Test
    fun ordinaryChineseSentences_keepTheEstablishedParagraphPreference() {
        val sentence = "正文始终完整，键盘不会触发下一拍。"
        val text = sentence.repeat(12)
        assertEquals(
            listOf(sentence.repeat(5), sentence.repeat(5), sentence.repeat(2)),
            CompanionDisplayParagraphs.split(text),
        )
    }

    @Test
    fun hardLimit_betweenHighAndLowSurrogates_keepsTheEmojiInOneText() {
        val text = "中".repeat(143) + "😀" + "后".repeat(200)
        val parts = CompanionDisplayParagraphs.split(text)
        assertEquals(text, parts.joinToString(""))
        assertEquals("中".repeat(143) + "😀", parts.first())
        assertIndependentUtf16(parts)
    }

    @Test
    fun combiningAndJoinedClusters_nearHardLimit_stayInSingleParagraphs() {
        val clusters = listOf("e\u0301", "👩🏽‍💻", "🇨🇳", "क्ष", "👩‍👩‍👧‍👦", "\u0600A", "\r\n")
        clusters.forEach { cluster ->
            val text = "中".repeat(143) + cluster + "后".repeat(200)
            val parts = CompanionDisplayParagraphs.split(text)
            assertEquals(text, parts.joinToString(""))
            assertTrue("完整簇必须存在于一个独立Text：$cluster", parts.any { cluster in it })
            assertIndependentUtf16(parts)
        }
    }

    @Test
    fun aSingleOversizedCluster_isWholeAndFollowingTextStillProgresses() {
        val cluster = "a" + "\u0301".repeat(512)
        val text = cluster + "尾".repeat(200)
        val parts = CompanionDisplayParagraphs.split(text)
        assertEquals(cluster, parts.first())
        assertEquals(text, parts.joinToString(""))
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.isNotEmpty() })
        assertIndependentUtf16(parts)
    }

    private fun assertIndependentUtf16(parts: List<String>) {
        parts.forEach { part ->
            var index = 0
            while (index < part.length) {
                val char = part[index]
                assertFalse("独立Text不能从低代理开始或含孤立低代理", char.isLowSurrogate())
                if (char.isHighSurrogate()) {
                    assertTrue("独立Text中的高代理必须有同段低代理", index + 1 < part.length)
                    assertTrue(part[index + 1].isLowSurrogate())
                    index += 2
                } else index++
            }
        }
    }
}
