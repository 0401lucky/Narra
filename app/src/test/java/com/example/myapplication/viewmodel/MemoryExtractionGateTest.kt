package com.example.myapplication.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryExtractionGateTest {
    @Test
    fun shouldExtract_triggersWhenNewlyCompletedReachesWindow() {
        val gate = MemoryExtractionGate()
        assertFalse(gate.shouldExtract("c1", completedCount = 7, window = 8))
        assertTrue(gate.shouldExtract("c1", completedCount = 8, window = 8))
    }

    @Test
    fun shouldExtract_triggersEvenWhenCountSkipsOverWindowMultiple() {
        // 核心修复：completed 计数从 7 跳到 9（跨过倍数点 8），
        // 旧的 `count % window == 0` 会永久丢失该窗口；水位线方案下 9 - 0 >= 8 仍应触发。
        val gate = MemoryExtractionGate()
        assertFalse(gate.shouldExtract("c1", completedCount = 7, window = 8))
        assertTrue(gate.shouldExtract("c1", completedCount = 9, window = 8))
    }

    @Test
    fun shouldExtract_doesNotReExtractSameSegmentBeforeNextWindow() {
        val gate = MemoryExtractionGate()
        assertTrue(gate.shouldExtract("c1", completedCount = 8, window = 8))
        // 水位线推进到 8，未再新增满一个窗口前不应重复触发同一段
        assertFalse(gate.shouldExtract("c1", completedCount = 9, window = 8))
        assertFalse(gate.shouldExtract("c1", completedCount = 15, window = 8))
        assertTrue(gate.shouldExtract("c1", completedCount = 16, window = 8))
    }

    @Test
    fun shouldExtract_returnsFalseForNonPositiveWindow() {
        val gate = MemoryExtractionGate()
        assertFalse(gate.shouldExtract("c1", completedCount = 100, window = 0))
        assertFalse(gate.shouldExtract("c1", completedCount = 100, window = -1))
    }

    @Test
    fun shouldExtract_returnsFalseForZeroCompleted() {
        val gate = MemoryExtractionGate()
        assertFalse(gate.shouldExtract("c1", completedCount = 0, window = 8))
    }

    @Test
    fun shouldExtract_tracksWatermarkPerConversation() {
        val gate = MemoryExtractionGate()
        assertTrue(gate.shouldExtract("c1", completedCount = 8, window = 8))
        // 另一会话有独立水位线，不受 c1 影响
        assertTrue(gate.shouldExtract("c2", completedCount = 8, window = 8))
        // c1 水位线已在 8，相同计数不再触发
        assertFalse(gate.shouldExtract("c1", completedCount = 8, window = 8))
    }
}
