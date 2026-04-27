package com.example.myapplication.data.repository.context

import com.example.myapplication.model.ContextGovernanceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextLogStoreTest {

    @Test
    fun `push respects default capacity 15`() {
        val store = ContextLogStore()
        repeat(20) { idx ->
            store.push(snapshot("s$idx"))
        }

        val current = store.snapshots.value
        assertEquals(15, current.size)
        assertEquals("s19", current.first().id)
        assertEquals("s5", current.last().id)
    }

    @Test
    fun `setCapacity truncates existing snapshots when shrinking`() {
        val store = ContextLogStore()
        repeat(10) { idx -> store.push(snapshot("s$idx")) }
        assertEquals(10, store.snapshots.value.size)

        store.setCapacity(5)

        val current = store.snapshots.value
        assertEquals(5, current.size)
        assertEquals("s9", current.first().id)
        assertEquals("s5", current.last().id)
    }

    @Test
    fun `setCapacity coerces values into allowed range`() {
        val store = ContextLogStore()
        store.setCapacity(0)  // below CONTEXT_LOG_CAPACITY_MIN(=5)

        repeat(10) { idx -> store.push(snapshot("s$idx")) }

        // capacity should have been coerced to MIN=5
        assertEquals(5, store.snapshots.value.size)
    }

    @Test
    fun `push is a no-op when disabled`() {
        val store = ContextLogStore()
        store.setEnabled(false)
        store.push(snapshot("s0"))

        assertTrue(store.snapshots.value.isEmpty())
    }

    @Test
    fun `disabling does not clear existing snapshots so user can review them`() {
        val store = ContextLogStore()
        store.push(snapshot("s0"))
        assertEquals(1, store.snapshots.value.size)

        store.setEnabled(false)
        assertEquals(1, store.snapshots.value.size)

        // re-enabled writes resume
        store.setEnabled(true)
        store.push(snapshot("s1"))
        assertEquals(2, store.snapshots.value.size)
    }

    @Test
    fun `push deduplicates by snapshot id`() {
        val store = ContextLogStore()
        store.push(snapshot("s0", payload = "first"))
        store.push(snapshot("s0", payload = "second"))

        val current = store.snapshots.value
        assertEquals(1, current.size)
        assertEquals("second", current.first().rawDebugDump)
    }

    @Test
    fun `removeById removes a snapshot but enabled state stays`() {
        val store = ContextLogStore()
        store.push(snapshot("s0"))
        store.push(snapshot("s1"))

        store.removeById("s0")

        val current = store.snapshots.value
        assertEquals(1, current.size)
        assertEquals("s1", current.first().id)
        assertFalse(current.any { it.id == "s0" })
    }

    private fun snapshot(id: String, payload: String = ""): ContextGovernanceSnapshot {
        return ContextGovernanceSnapshot(
            id = id,
            rawDebugDump = payload,
        )
    }
}
