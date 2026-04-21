package com.example.myapplication.context

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldBookMatcherTextUtilsTest {
    @Test
    fun cjkPattern_hitsInsideCjkRun() {
        assertTrue(matchesContainsCjkAware("白塔城", "我去白塔城的路上", caseSensitive = true))
    }

    @Test
    fun cjkPattern_hitsAtBoundary() {
        assertTrue(matchesContainsCjkAware("白塔城", "白塔城", caseSensitive = true))
        assertTrue(matchesContainsCjkAware("白塔城", "白塔城xyz", caseSensitive = true))
        assertTrue(matchesContainsCjkAware("白塔城", "xyz白塔城", caseSensitive = true))
    }

    @Test
    fun latinPattern_requiresWordBoundary() {
        assertTrue(matchesContainsCjkAware("foo", "the foo bar", caseSensitive = false))
        assertFalse(matchesContainsCjkAware("foo", "football", caseSensitive = false))
        assertFalse(matchesContainsCjkAware("foo", "myfoo", caseSensitive = false))
    }

    @Test
    fun latinPattern_allowsCjkAsBoundary() {
        // "foo" 紧挨 CJK 字符时视为词边界（CJK 字符不是 ASCII 字母数字）
        assertTrue(matchesContainsCjkAware("foo", "说foo呢", caseSensitive = false))
        assertTrue(matchesContainsCjkAware("foo", "中文foo", caseSensitive = false))
    }

    @Test
    fun mixedPattern_cjkHeadLatinTail_requiresTailBoundary() {
        assertTrue(matchesContainsCjkAware("白foo", "白foo bar", caseSensitive = false))
        assertFalse(matchesContainsCjkAware("白foo", "白foobar", caseSensitive = false))
    }

    @Test
    fun caseSensitive_isRespected() {
        assertTrue(matchesContainsCjkAware("Foo", "the foo bar", caseSensitive = false))
        assertFalse(matchesContainsCjkAware("Foo", "the foo bar", caseSensitive = true))
    }

    @Test
    fun emptyPattern_returnsFalse() {
        assertFalse(matchesContainsCjkAware("", "anything", caseSensitive = true))
        assertFalse(matchesContainsCjkAware("  ", "anything", caseSensitive = true))
    }
}
