package com.example.myapplication.data.repository.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayEconomyEventBusTest {
    @Test
    fun consume_returnsPostedEventsThenClears() {
        val bus = RoleplayEconomyEventBus()
        bus.post("s1", RoleplayEconomyEvent(RoleplayEconomyEventType.GIFTED, "铜纽扣", "引出旧约定"))
        bus.post("s1", RoleplayEconomyEvent(RoleplayEconomyEventType.USED, "旧照片"))

        val first = bus.consume("s1")
        assertEquals(2, first.size)
        assertEquals("铜纽扣", first[0].itemName)

        val second = bus.consume("s1")
        assertTrue(second.isEmpty())
    }

    @Test
    fun consume_isScopedByScenario() {
        val bus = RoleplayEconomyEventBus()
        bus.post("s1", RoleplayEconomyEvent(RoleplayEconomyEventType.PURCHASED, "A"))
        assertTrue(bus.consume("s2").isEmpty())
        assertEquals(1, bus.consume("s1").size)
    }

    @Test
    fun formatEconomyEventNote_buildsDirectorInstructionPerType() {
        val note = formatEconomyEventNote(
            listOf(
                RoleplayEconomyEvent(RoleplayEconomyEventType.GIFTED, "铜纽扣", "引出旧约定"),
                RoleplayEconomyEvent(RoleplayEconomyEventType.USED, "旧照片"),
                RoleplayEconomyEvent(RoleplayEconomyEventType.PURCHASED, "热可可"),
            ),
        )
        assertTrue(note.contains("【道具刚刚发生的事】"))
        assertTrue(note.contains("送给你《铜纽扣》"))
        assertTrue(note.contains("引出旧约定"))
        assertTrue(note.contains("使用了《旧照片》"))
        assertTrue(note.contains("买下了《热可可》"))
        assertTrue(note.contains("作出符合人设的即时反应"))
    }

    @Test
    fun formatEconomyEventNote_emptyReturnsBlank() {
        assertEquals("", formatEconomyEventNote(emptyList()))
    }
}
