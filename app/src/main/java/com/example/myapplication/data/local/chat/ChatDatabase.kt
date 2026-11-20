package com.example.myapplication.data.local.chat

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.myapplication.data.local.chat.migrations.ChatDbMigrations
import com.example.myapplication.data.local.economy.InventoryItemEntity
import com.example.myapplication.data.local.economy.RoleplayEconomyDao
import com.example.myapplication.data.local.economy.ShopBatchEntity
import com.example.myapplication.data.local.economy.ShopItemEntity
import com.example.myapplication.data.local.economy.WalletAccountEntity
import com.example.myapplication.data.local.economy.WalletLedgerEntryEntity
import com.example.myapplication.data.local.mailbox.MailboxDao
import com.example.myapplication.data.local.mailbox.MailboxLetterEntity
import com.example.myapplication.data.local.mailbox.MailboxSettingsEntity
import com.example.myapplication.data.local.memory.ConversationSummaryEntity
import com.example.myapplication.data.local.memory.ConversationSummarySegmentEntity
import com.example.myapplication.data.local.memory.MemoryDao
import com.example.myapplication.data.local.memory.MemoryEntryEntity
import com.example.myapplication.data.local.memory.MemoryArchiveDao
import com.example.myapplication.data.local.memory.MemorySpaceEntity
import com.example.myapplication.data.local.memory.MemoryConversationBindingEntity
import com.example.myapplication.data.local.memory.MemoryRecordEntity
import com.example.myapplication.data.local.memory.MemoryRecordRevisionEntity
import com.example.myapplication.data.local.memory.MemorySourceEvidenceEntity
import com.example.myapplication.data.local.memory.MemoryRecordSourceEntity
import com.example.myapplication.data.local.memory.MemoryIngressEventEntity
import com.example.myapplication.data.local.memory.MemoryProcessingJobEntity
import com.example.myapplication.data.local.memory.MemoryJobSourceEntity
import com.example.myapplication.data.local.memory.MemoryProcessingCursorEntity
import com.example.myapplication.data.local.memory.MemoryRelationIntentEntity
import com.example.myapplication.data.local.memory.MemoryEngineStateEntity
import com.example.myapplication.data.local.memory.MemoryEventBoxEntity
import com.example.myapplication.data.local.memory.MemoryEventMemberEntity
import com.example.myapplication.data.local.memory.MemoryResidentEntryEntity
import com.example.myapplication.data.local.memory.MemoryCurrentStateEntity
import com.example.myapplication.data.local.memory.MemoryDerivedLinkEntity
import com.example.myapplication.data.local.memory.MemoryEmbeddingIndexEntity
import com.example.myapplication.data.local.memory.MemoryEmbeddingEntity
import com.example.myapplication.data.local.memory.MemoryRecallReceiptEntity
import com.example.myapplication.data.local.memory.MemoryProposalEntity
import com.example.myapplication.data.local.moments.MomentCommentEntity
import com.example.myapplication.data.local.moments.MomentDao
import com.example.myapplication.data.local.moments.MomentMediaEntity
import com.example.myapplication.data.local.moments.MomentPostEntity
import com.example.myapplication.data.local.phone.PhoneObservationEntity
import com.example.myapplication.data.local.phone.PhoneSnapshotDao
import com.example.myapplication.data.local.phone.PhoneSnapshotEntity
import com.example.myapplication.data.local.preset.PresetDao
import com.example.myapplication.data.local.preset.PresetEntity
import com.example.myapplication.data.local.roleplay.RoleplayDao
import com.example.myapplication.data.local.roleplay.RoleplayDiaryEntryEntity
import com.example.myapplication.data.local.roleplay.RoleplayGroupParticipantEntity
import com.example.myapplication.data.local.roleplay.RoleplayOnlineMetaEntity
import com.example.myapplication.data.local.roleplay.RoleplayScenarioEntity
import com.example.myapplication.data.local.roleplay.RoleplaySessionEntity
import com.example.myapplication.data.local.roleplay.script.RoleplayScriptDao
import com.example.myapplication.data.local.roleplay.script.RoleplayScriptEntity
import com.example.myapplication.data.local.roleplay.script.RoleplayScriptStateEntity
import com.example.myapplication.data.local.worldbook.WorldBookDao
import com.example.myapplication.data.local.worldbook.WorldBookEntryEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        com.example.myapplication.data.local.companion.CompanionEncounterEntity::class,
        com.example.myapplication.data.local.companion.CompanionTurnEntity::class,
        com.example.myapplication.data.local.companion.CompanionAttemptEntity::class,
        com.example.myapplication.data.local.companion.CompanionVoiceResourceEntity::class,
        com.example.myapplication.data.local.companion.CompanionVoiceFavoriteEntity::class,
        com.example.myapplication.data.local.companion.ContextTransferOperationEntity::class,
        WorldBookEntryEntity::class,
        MemoryEntryEntity::class,
        ConversationSummaryEntity::class,
        ConversationSummarySegmentEntity::class,
        RoleplayScenarioEntity::class,
        RoleplayGroupParticipantEntity::class,
        RoleplaySessionEntity::class,
        RoleplayOnlineMetaEntity::class,
        RoleplayDiaryEntryEntity::class,
        PhoneSnapshotEntity::class,
        PhoneObservationEntity::class,
        MailboxLetterEntity::class,
        MailboxSettingsEntity::class,
        PresetEntity::class,
        RoleplayScriptEntity::class,
        RoleplayScriptStateEntity::class,
        MomentPostEntity::class,
        MomentCommentEntity::class,
        MomentMediaEntity::class,
        WalletAccountEntity::class,
        WalletLedgerEntryEntity::class,
        ShopBatchEntity::class,
        ShopItemEntity::class,
        InventoryItemEntity::class,
        MemorySpaceEntity::class,
        MemoryConversationBindingEntity::class,
        MemoryRecordEntity::class,
        MemoryRecordRevisionEntity::class,
        MemorySourceEvidenceEntity::class,
        MemoryRecordSourceEntity::class,
        MemoryIngressEventEntity::class,
        MemoryProcessingJobEntity::class,
        MemoryJobSourceEntity::class,
        MemoryProcessingCursorEntity::class,
        MemoryRelationIntentEntity::class,
        MemoryEngineStateEntity::class,
        MemoryEventBoxEntity::class,
        MemoryEventMemberEntity::class,
        MemoryResidentEntryEntity::class,
        MemoryCurrentStateEntity::class,
        MemoryDerivedLinkEntity::class,
        MemoryEmbeddingIndexEntity::class,
        MemoryEmbeddingEntity::class,
        MemoryRecallReceiptEntity::class,
        MemoryProposalEntity::class,
        com.example.myapplication.data.local.memory.MemoryKnowledgeRevisionEntity::class,
        com.example.myapplication.data.local.memory.MemoryKnowledgeSourceEntity::class,
        com.example.myapplication.data.local.memory.MemoryKnowledgeConsumptionEntity::class,
        com.example.myapplication.data.local.memory.MemoryProcessingControlEntity::class,
        com.example.myapplication.data.local.memory.MemoryTransferOriginEntity::class,
        com.example.myapplication.data.local.memory.MemoryTransferReceiptEntity::class,
    ],
    version = ChatDatabase.CURRENT_VERSION,
    exportSchema = true,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun companionDao(): com.example.myapplication.data.local.companion.CompanionDao
    abstract fun companionVoiceDao(): com.example.myapplication.data.local.companion.CompanionVoiceDao
    abstract fun contextTransferOperationDao(): com.example.myapplication.data.local.companion.ContextTransferOperationDao
    abstract fun worldBookDao(): WorldBookDao
    abstract fun memoryDao(): MemoryDao
    abstract fun memoryArchiveDao(): MemoryArchiveDao
    abstract fun memoryKnowledgeDao(): com.example.myapplication.data.local.memory.MemoryKnowledgeDao
    abstract fun memoryVectorDao(): com.example.myapplication.data.local.memory.MemoryVectorDao
    abstract fun memoryTransferDao(): com.example.myapplication.data.local.memory.MemoryTransferDao
    abstract fun roleplayDao(): RoleplayDao
    abstract fun phoneSnapshotDao(): PhoneSnapshotDao
    abstract fun mailboxDao(): MailboxDao
    abstract fun presetDao(): PresetDao
    abstract fun roleplayScriptDao(): RoleplayScriptDao
    abstract fun momentDao(): MomentDao
    abstract fun roleplayEconomyDao(): RoleplayEconomyDao

    companion object {
        const val CURRENT_VERSION = 57

        /**
         * 所有 Room 迁移。具体 DDL 在 [ChatDbMigrations]，这里只暴露注册表给 `AppGraph` 和测试。
         * 连续性由 `ChatDatabaseMigrationRegistryTest` 保证。
         */
        val ALL_MIGRATIONS = ChatDbMigrations.ALL
    }
}
