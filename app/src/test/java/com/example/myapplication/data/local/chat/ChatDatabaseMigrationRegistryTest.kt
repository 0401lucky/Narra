package com.example.myapplication.data.local.chat

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDatabaseMigrationRegistryTest {
    @Test
    fun allMigrations_containsLatestRegisteredMigration() {
        assertTrue(ChatDatabase.ALL_MIGRATIONS.isNotEmpty())
        assertSame(
            ChatDatabase.MIGRATION_22_23,
            ChatDatabase.ALL_MIGRATIONS.last(),
        )
    }
}
