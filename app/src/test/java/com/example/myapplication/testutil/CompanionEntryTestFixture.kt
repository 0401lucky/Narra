package com.example.myapplication.testutil

import com.example.myapplication.context.DefaultPromptContextAssembler
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.conversation.CompanionRequestCoordinator
import com.example.myapplication.conversation.CompanionTurnCoordinator
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.DefaultAiGateway
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.data.repository.roleplay.CompanionHistoryRepository
import com.example.myapplication.data.repository.roleplay.CompanionRepository
import com.example.myapplication.data.repository.roleplay.RoomCompanionHistoryRepository
import com.example.myapplication.data.repository.roleplay.RoomCompanionRepository
import com.example.myapplication.data.repository.roleplay.RoomRoleplayRepository
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.search.SearchResult
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

open class CompanionEntryTestFixture : CompanionTestFixture() {
    protected lateinit var server: MockWebServer
    protected lateinit var history: RoomCompanionHistoryRepository
    protected lateinit var conversations: ConversationRepository
    protected lateinit var settings: FakeSettingsStore
    protected var commits = 0
    protected val worldBook = FakeWorldBookRepository()

    @Before
    fun configureEntryFixture() {
        server = MockWebServer().also { it.start() }
        configureRepositories()
        val provider = ProviderSettings(
            id = "fixture", baseUrl = server.url("/v1/").toString(),
            apiKey = "synthetic-companion-key", selectedModel = "fixture-chat"
        )
        settings = FakeSettingsStore(
            AppSettings(
                providers = listOf(provider), selectedProviderId = provider.id,
                assistants = listOf(
                    Assistant(
                        id = "alice",
                        name = "艾丽",
                        systemPrompt = "甲角色事实哨兵",
                        greeting = "静态问候哨兵",
                        contextMessageSize = 24
                    ),
                    Assistant(id = "bob", name = "伯比", systemPrompt = "乙角色事实哨兵")
                )
            )
        )
    }

    protected fun configureRepositories() {
        history = RoomCompanionHistoryRepository(database, archive)
        repository = RoomCompanionRepository(
            database,
            archive,
            { clock++ },
            requestsAllowed = { allowed },
            historyAllowed = history::validate
        )
        conversations = ConversationRepository(store, nowProvider = { clock++ })
        roleplay = RoomRoleplayRepository(
            database.roleplayDao(), conversations, { clock++ },
            database = database, memoryArchiveRepository = archive, companionRepository = repository
        )
    }

    @After
    fun closeEntryServer() {
        server.shutdown()
    }

    protected fun requests(
        gateway: AiGateway = gateway(),
        historyRepository: CompanionHistoryRepository = history,
        companionRepository: CompanionRepository = repository,
        assembler: PromptContextAssembler = DefaultPromptContextAssembler(
            memoryArchiveRepository = archive,
            worldBookRepository = worldBook
        ),
        settingsStore: SettingsStore = settings,
    ) = CompanionRequestCoordinator(
        companionRepository,
        CompanionTurnCoordinator(companionRepository, onCommitted = { commits++ }),
        historyRepository,
        roleplay,
        conversations,
        archive,
        settingsStore,
        assembler,
        gateway,
        { clock++ },
        deviceZone = { ZoneId.of("UTC") })

    protected fun gateway(
        archiveRepository: MemoryArchiveRepository = archive,
        resolveImage: suspend (MessageAttachment) -> String = { "data:image/png;base64,aGVsbG8=" },
        settingsStore: SettingsStore = settings,
    ): AiGateway = DefaultAiGateway(
        settingsStore, ApiServiceFactory(),
        searchRepository = object : SearchRepository {
            override suspend fun search(
                source: SearchSourceConfig,
                query: String,
                resultCount: Int
            ): SearchResult =
                error("陪伴请求不应启用搜索工具")
        }, memoryArchiveRepository = archiveRepository, imagePayloadResolver = resolveImage
    )

    protected suspend fun scene(
        id: String = "scene-${UUID.randomUUID()}",
        character: String = "alice",
        purpose: MemoryPurpose = MemoryPurpose.CONTINUOUS,
        persona: String = "身份甲",
        mode: RoleplayInteractionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
        greeting: String = "",
    ): MemoryRuntimeContext {
        roleplay.upsertScenario(
            RoleplayScenario(
                id = id, assistantId = character, title = "合成会面",
                memoryPurpose = purpose, userPersonaOverride = persona, openingNarration = greeting,
                interactionMode = mode, enableTimeAwareness = false
            )
        )
        val session = roleplay.startScenario(id).session
        return requireNotNull(archive.getRuntimeContext(session.conversationId, character))
    }

    protected suspend fun add(
        context: MemoryRuntimeContext, content: String, id: String = UUID.randomUUID().toString(),
        role: MessageRole = MessageRole.USER, parts: List<ChatMessagePart> = emptyList()
    ): ChatMessage {
        store.upsertMessages(
            listOf(
                ChatMessage(
                    id,
                    context.conversationId,
                    role,
                    content,
                    createdAt = clock++,
                    parts = parts,
                    speakerId = if (role == MessageRole.ASSISTANT) context.characterId else ""
                )
            )
        )
        return store.listMessages(context.conversationId).first { it.id == id }
    }

    protected suspend fun protocol(
        mode: OpenAiTextApiMode = OpenAiTextApiMode.CHAT_COMPLETIONS,
        api: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE
    ) {
        val provider = settings.settingsFlow.first().providers.single()
            .copy(openAiTextApiMode = mode, apiProtocol = api)
        settings.saveProviderSettings(listOf(provider), provider.id)
    }

    protected fun reply(
        text: String = "<dialogue>好，就坐一会儿。</dialogue>",
        mode: OpenAiTextApiMode = OpenAiTextApiMode.CHAT_COMPLETIONS,
        api: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE
    ) {
        val body = when {
            api == ProviderApiProtocol.ANTHROPIC -> mapOf(
                "id" to "fixture",
                "type" to "message",
                "role" to "assistant",
                "content" to listOf(mapOf("type" to "text", "text" to text)),
                "stop_reason" to "end_turn"
            )

            mode == OpenAiTextApiMode.RESPONSES -> mapOf(
                "id" to "fixture", "status" to "completed", "output" to listOf(
                    mapOf(
                        "type" to "message",
                        "role" to "assistant",
                        "content" to listOf(mapOf("type" to "output_text", "text" to text))
                    )
                )
            )

            else -> mapOf(
                "id" to "fixture",
                "choices" to listOf(
                    mapOf(
                        "message" to mapOf(
                            "role" to "assistant",
                            "content" to text
                        ), "finish_reason" to "stop"
                    )
                )
            )
        }
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody(AppJson.gson.toJson(body))
        )
    }

    protected fun payload(): String =
        requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)).body.readUtf8()
}
