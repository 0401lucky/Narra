package com.example.myapplication.ui.component.worldbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordChipInputTest {
    @Test
    fun split_basicCommaDelimitedList() {
        val result = splitKeywordsPreservingRegex("主角, 配角，路人")
        assertEquals(listOf("主角", "配角", "路人"), result)
    }

    @Test
    fun split_preservesRegexLiteralContainingComma() {
        val result = splitKeywordsPreservingRegex("/foo,bar/i, 主角")
        assertEquals(listOf("/foo,bar/i", "主角"), result)
    }

    @Test
    fun split_regexWithEscapedSlashNotCut() {
        val result = splitKeywordsPreservingRegex("""/a\/b/,x""")
        assertEquals(listOf("""/a\/b/""", "x"), result)
    }

    @Test
    fun split_trimsAndDedupes() {
        val result = splitKeywordsPreservingRegex("  主角 ,  主角 ,  ")
        assertEquals(listOf("主角"), result)
    }

    @Test
    fun split_handlesNewlineSeparator() {
        val result = splitKeywordsPreservingRegex("主角\n配角\n/a,b/i")
        assertEquals(listOf("主角", "配角", "/a,b/i"), result)
    }

    @Test
    fun regexLiteralDetector_accepts() {
        assertTrue(looksLikeWorldBookRegexLiteral("/foo/"))
        assertTrue(looksLikeWorldBookRegexLiteral("/foo/i"))
        assertTrue(looksLikeWorldBookRegexLiteral("/a,b/im"))
        assertTrue(looksLikeWorldBookRegexLiteral("""/a\/b/"""))
    }

    @Test
    fun regexLiteralDetector_rejects() {
        assertFalse(looksLikeWorldBookRegexLiteral("主角"))
        assertFalse(looksLikeWorldBookRegexLiteral("/incomplete"))
        assertFalse(looksLikeWorldBookRegexLiteral("//"))
    }
}
