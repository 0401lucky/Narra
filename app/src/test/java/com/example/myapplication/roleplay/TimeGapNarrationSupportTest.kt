package com.example.myapplication.roleplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class TimeGapNarrationSupportTest {

    @Test
    fun buildTimeGapNarration_belowThresholdReturnsNull() {
        val last = System.currentTimeMillis()
        val current = last + TimeUnit.MINUTES.toMillis(20) // 20 分钟，低于 30 分钟阈值
        assertNull(TimeGapNarrationSupport.buildTimeGapNarration(last, current))
    }

    @Test
    fun buildTimeGapNarration_atThresholdReturnsNarration() {
        val last = System.currentTimeMillis()
        val current = last + TimeUnit.MINUTES.toMillis(30)
        val result = TimeGapNarrationSupport.buildTimeGapNarration(last, current)
        assertNotNull(result)
        assertTrue(result!!.contains("30分钟"))
        assertTrue(result.contains("【时间旁白】"))
    }

    @Test
    fun buildTimeGapNarration_oneHourShowsHoursAndMinutes() {
        val last = System.currentTimeMillis()
        val current = last + TimeUnit.MINUTES.toMillis(90) // 1.5 小时
        val result = TimeGapNarrationSupport.buildTimeGapNarration(last, current)
        assertNotNull(result)
        assertTrue(result!!.contains("1小时30分钟"))
    }

    @Test
    fun buildTimeGapNarration_mediumGapShowsDecimalHours() {
        val last = System.currentTimeMillis()
        val current = last + TimeUnit.HOURS.toMillis(4) + TimeUnit.MINUTES.toMillis(30) // 4.5h
        val result = TimeGapNarrationSupport.buildTimeGapNarration(last, current)
        assertNotNull(result)
        assertTrue(result!!.contains("4.5小时"))
    }

    @Test
    fun buildTimeGapNarration_longGapShowsDaysAndHours() {
        val last = System.currentTimeMillis()
        val current = last + TimeUnit.DAYS.toMillis(2) + TimeUnit.HOURS.toMillis(3) // 2天3小时
        val result = TimeGapNarrationSupport.buildTimeGapNarration(last, current)
        assertNotNull(result)
        assertTrue(result!!.contains("2天3小时"))
    }

    @Test
    fun buildTimeGapNarration_invalidTimestampsReturnNull() {
        assertNull(TimeGapNarrationSupport.buildTimeGapNarration(0L, System.currentTimeMillis()))
        assertNull(TimeGapNarrationSupport.buildTimeGapNarration(-1L, System.currentTimeMillis()))
    }

    @Test
    fun buildTimeGapNarration_reverseTimestampsReturnNull() {
        val now = System.currentTimeMillis()
        assertNull(TimeGapNarrationSupport.buildTimeGapNarration(now, now - TimeUnit.HOURS.toMillis(1)))
    }

    @Test
    fun formatGapDescription_exactHoursNoDecimal() {
        val result = TimeGapNarrationSupport.formatGapDescription(TimeUnit.HOURS.toMillis(3))
        assertEquals("3小时", result)
    }

    @Test
    fun formatGapDescription_exactDayNoDaysHoursPart() {
        val result = TimeGapNarrationSupport.formatGapDescription(TimeUnit.DAYS.toMillis(1))
        assertEquals("1天", result)
    }
}
