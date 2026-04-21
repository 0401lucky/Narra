package com.example.myapplication.data.local.chat

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.myapplication.data.local.chat.migrations.ChatDbMigrations
import com.example.myapplication.data.local.memory.ConversationSummaryEntity
import com.example.myapplication.data.local.memory.MemoryDao
import com.example.myapplication.data.local.memory.MemoryEntryEntity
import com.example.myapplication.data.local.phone.PhoneObservationEntity
import com.example.myapplication.data.local.phone.PhoneSnapshotDao
import com.example.myapplication.data.local.phone.PhoneSnapshotEntity
import com.example.myapplication.data.local.roleplay.RoleplayDao
import com.example.myapplication.data.local.roleplay.RoleplayDiaryEntryEntity
import com.example.myapplication.data.local.roleplay.RoleplayOnlineMetaEntity
import com.example.myapplication.data.local.roleplay.RoleplayScenarioEntity
import com.example.myapplication.data.local.roleplay.RoleplaySessionEntity
import com.example.myapplication.data.local.worldbook.WorldBookDao
import com.example.myapplication.data.local.worldbook.WorldBookEntryEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        WorldBookEntryEntity::class,
        MemoryEntryEntity::class,
        ConversationSummaryEntity::class,
        RoleplayScenarioEntity::class,
        RoleplaySessionEntity::class,
        RoleplayOnlineMetaEntity::class,
        RoleplayDiaryEntryEntity::class,
        PhoneSnapshotEntity::class,
        PhoneObservationEntity::class,
    ],
    version = ChatDatabase.CURRENT_VERSION,
    exportSchema = true,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun worldBookDao(): WorldBookDao
    abstract fun memoryDao(): MemoryDao
    abstract fun roleplayDao(): RoleplayDao
    abstract fun phoneSnapshotDao(): PhoneSnapshotDao

    companion object {
        const val CURRENT_VERSION = 30

        /**
         * 所有 Room 迁移。具体 DDL 在 [ChatDbMigrations]，这里只暴露注册表给 `AppGraph` 和测试。
         * 连续性由 `ChatDatabaseMigrationRegistryTest` 保证。
         */
        val ALL_MIGRATIONS = ChatDbMigrations.ALL
    }
}
