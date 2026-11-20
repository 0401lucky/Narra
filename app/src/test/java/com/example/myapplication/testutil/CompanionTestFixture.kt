package com.example.myapplication.testutil

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.context.RoomMemoryArchiveRepository
import com.example.myapplication.data.repository.roleplay.RoomCompanionRepository
import com.example.myapplication.data.repository.roleplay.RoomRoleplayRepository
import com.example.myapplication.model.*
import org.junit.After
import org.junit.Before
import java.util.UUID

open class CompanionTestFixture {
    protected lateinit var application: Context
    protected lateinit var database: ChatDatabase
    protected lateinit var store: RoomConversationStore
    protected lateinit var archive: RoomMemoryArchiveRepository
    protected lateinit var roleplay: RoomRoleplayRepository
    protected lateinit var repository: RoomCompanionRepository
    protected var clock = 1_000L
    protected var allowed = true
    protected var characters = setOf("alice", "bob")
    private val names = mutableListOf<String>()
    private var currentName: String? = null

    @Before
    fun openCompanionFixture() {
        application = ApplicationProvider.getApplicationContext<Application>()
        open()
    }

    protected fun useFileDatabase() {
        database.close()
        currentName = "cp-" + UUID.randomUUID().toString().take(8)
        names += currentName!!
        open()
    }

    protected fun reopen() {
        database.close(); open()
    }

    private fun open() {
        database =
            (currentName?.let { Room.databaseBuilder(application, ChatDatabase::class.java, it) }
                ?: Room.inMemoryDatabaseBuilder(application, ChatDatabase::class.java))
                .allowMainThreadQueries().build()
        store = RoomConversationStore(database) { clock++ }
        archive = RoomMemoryArchiveRepository(database, { clock++ }, { it in characters })
        roleplay = RoomRoleplayRepository(
            database.roleplayDao(), ConversationRepository(store, nowProvider = { clock++ }),
            nowProvider = { clock++ }, database = database, memoryArchiveRepository = archive,
        )
        repository =
            RoomCompanionRepository(database, archive, { clock++ }, requestsAllowed = { allowed })
    }

    @After
    fun closeCompanionFixture() {
        database.close()
        names.forEach { application.deleteDatabase(it) }
    }

    protected suspend fun runtime(character: String = "alice"): MemoryRuntimeContext {
        val scene = RoleplayScenario(
            id = "scene-" + UUID.randomUUID(), title = "合成陪伴", assistantId = character,
            memoryPurpose = MemoryPurpose.CONTINUOUS, openingNarration = "",
        )
        roleplay.upsertScenario(scene)
        val session = roleplay.startScenario(scene.id).session
        return requireNotNull(archive.getRuntimeContext(session.conversationId, character))
    }

    protected suspend fun prepared(
        context: MemoryRuntimeContext? = null,
        kind: CompanionTurnKind = CompanionTurnKind.INPUT,
        mode: CompanionOpeningMode = CompanionOpeningMode.DIRECT,
        text: String = "一起坐一会儿",
    ): CompanionPreparedTurn {
        val resolvedContext = context ?: runtime()
        val encounter = repository.createEncounter(resolvedContext, mode)
        val input = if (kind == CompanionTurnKind.INPUT)
            ChatMessage(
                "input-" + UUID.randomUUID(),
                resolvedContext.conversationId,
                MessageRole.USER,
                text,
                createdAt = clock++
            )
        else null
        return repository.beginTurn(encounter.id, kind, input)
    }

    protected suspend fun received(
        turn: CompanionPreparedTurn,
        text: String = "<dialogue>好，就坐一会儿。</dialogue>",
        status: PromptCompletionStatus = PromptCompletionStatus.COMPLETED,
        owner: String = "fixture-owner",
    ) {
        check(repository.claimAttempt(turn.attempt.id, owner) != null)
        check(
            repository.saveGeneratedArtifact(
                turn.attempt.id,
                owner,
                CompanionGeneratedArtifact(text, status)
            )
        )
    }

    protected fun sql(text: String) = database.openHelper.writableDatabase.execSQL(text)
}
