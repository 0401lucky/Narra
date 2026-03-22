package com.example.myapplication

import android.app.Application
import androidx.room.Room
import com.example.myapplication.context.DefaultPromptContextAssembler
import com.example.myapplication.context.MemorySelector
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.context.WorldBookMatcher
import com.example.myapplication.data.local.AppSettingsStore
import com.example.myapplication.data.local.AppUpdateStore
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.data.repository.AppUpdateRepository
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.FileAttachmentResolver
import com.example.myapplication.data.repository.ImageAttachmentResolver
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoomRoleplayRepository
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.RoomMemoryRepository
import com.example.myapplication.data.repository.context.RoomWorldBookRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.system.update.AndroidAppUpdateController
import com.example.myapplication.system.update.AppUpdateDownloadController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChatApplication : Application() {
    lateinit var conversationRepository: ConversationRepository
        private set
    lateinit var aiRepository: AiRepository
        private set
    lateinit var worldBookRepository: WorldBookRepository
        private set
    lateinit var memoryRepository: MemoryRepository
        private set
    lateinit var conversationSummaryRepository: ConversationSummaryRepository
        private set
    lateinit var promptContextAssembler: PromptContextAssembler
        private set
    lateinit var roleplayRepository: RoleplayRepository
        private set
    lateinit var appUpdateRepository: AppUpdateRepository
        private set
    lateinit var appUpdateDownloadController: AppUpdateDownloadController
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            this,
            ChatDatabase::class.java,
            CHAT_DATABASE_NAME,
        ).addMigrations(
            ChatDatabase.MIGRATION_1_2,
            ChatDatabase.MIGRATION_2_3,
            ChatDatabase.MIGRATION_3_4,
            ChatDatabase.MIGRATION_4_5,
            ChatDatabase.MIGRATION_5_6,
            ChatDatabase.MIGRATION_6_7,
            ChatDatabase.MIGRATION_7_8,
            ChatDatabase.MIGRATION_8_9,
            ChatDatabase.MIGRATION_9_10,
        ).build()

        val settingsStore = AppSettingsStore(this)
        val appUpdateStore = AppUpdateStore(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            settingsStore.migrateSensitiveData()
        }
        conversationRepository = ConversationRepository(
            conversationStore = RoomConversationStore(database.conversationDao()),
        )
        aiRepository = AiRepository(
            settingsStore = settingsStore,
            apiServiceFactory = ApiServiceFactory(),
            imagePayloadResolver = ImageAttachmentResolver(this)::resolveDataUrl,
            filePromptResolver = FileAttachmentResolver(this)::resolvePromptText,
        )
        worldBookRepository = RoomWorldBookRepository(database.worldBookDao())
        val roomMemoryRepository = RoomMemoryRepository(database.memoryDao())
        memoryRepository = roomMemoryRepository
        conversationSummaryRepository = roomMemoryRepository
        roleplayRepository = RoomRoleplayRepository(
            roleplayDao = database.roleplayDao(),
            conversationRepository = conversationRepository,
        )
        promptContextAssembler = DefaultPromptContextAssembler(
            worldBookRepository = worldBookRepository,
            worldBookMatcher = WorldBookMatcher(),
            memoryRepository = memoryRepository,
            memorySelector = MemorySelector(),
            conversationSummaryRepository = conversationSummaryRepository,
        )
        appUpdateRepository = AppUpdateRepository(
            stateStore = appUpdateStore,
        )
        appUpdateDownloadController = AndroidAppUpdateController(this)
    }

    private companion object {
        const val CHAT_DATABASE_NAME = "chat.db"
    }
}
