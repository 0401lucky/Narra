package com.example.myapplication.ui.screen.settings.worldbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WorldBookListStyleTest {

    @Test
    fun bookSpineColor_isStableForSameBookId() {
        val first = bookSpineColor("book-alpha")
        val second = bookSpineColor("book-alpha")
        assertEquals(first, second)
    }

    @Test
    fun bookSpineColor_differsBetweenDistinctBookIds() {
        val a = bookSpineColor("book-alpha")
        val b = bookSpineColor("book-beta")
        assertNotEquals(a, b)
    }

    @Test
    fun bookSpineColor_handlesBlankIdWithoutCrash() {
        // 不应抛异常；返回值具体为多少不 assert，防止回归
        bookSpineColor("")
    }
}
