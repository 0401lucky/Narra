package com.example.myapplication.data.local.chat

import com.example.myapplication.data.local.chat.migrations.ChatDbMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDatabaseMigrationRegistryTest {
    @Test
    fun allMigrations_containsLatestRegisteredMigration() {
        assertTrue(ChatDatabase.ALL_MIGRATIONS.isNotEmpty())
        assertSame(
            ChatDbMigrations.MIGRATION_31_32,
            ChatDatabase.ALL_MIGRATIONS.last(),
        )
    }

    @Test
    fun allMigrations_coversEveryVersionContiguously() {
        // 迁移数组必须覆盖 1→2、2→3、…、(N-1)→N；禁止跳版。
        val migrations = ChatDatabase.ALL_MIGRATIONS
        assertEquals(
            "ALL_MIGRATIONS size should equal CURRENT_VERSION - 1",
            ChatDatabase.CURRENT_VERSION - 1,
            migrations.size,
        )
        migrations.forEachIndexed { index, migration ->
            val expectedStart = index + 1
            assertEquals(
                "MIGRATION_${expectedStart}_${expectedStart + 1} missing or out of order at index $index",
                expectedStart,
                migration.startVersion,
            )
            assertEquals(
                "MIGRATION_${expectedStart}_${expectedStart + 1} end version incorrect",
                expectedStart + 1,
                migration.endVersion,
            )
        }
    }
}
