package com.example.myapplication.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedKeySetTest {

    @Test
    fun contains_returnsTrueForAddedKey_falseForOthers() {
        val set = BoundedKeySet(maxSize = 4)
        set += "a"
        assertTrue("a" in set)
        assertFalse("b" in set)
    }

    @Test
    fun add_beyondCapacity_staysBoundedAndEvictsOldest() {
        val set = BoundedKeySet(maxSize = 3)
        repeat(100) { index -> set += "key-$index" }
        // 大量写入后集合大小不无界增长，被钳制在容量上限
        assertEquals(3, set.size)
        // 最早写入的键已被淘汰
        assertFalse("key-0" in set)
        assertFalse("key-96" in set)
        // 最近写入的键保留
        assertTrue("key-97" in set)
        assertTrue("key-98" in set)
        assertTrue("key-99" in set)
    }

    @Test
    fun reAddingExistingKey_doesNotGrowSize() {
        val set = BoundedKeySet(maxSize = 3)
        set += "x"
        set += "x"
        set += "x"
        assertEquals(1, set.size)
        assertTrue("x" in set)
    }
}
