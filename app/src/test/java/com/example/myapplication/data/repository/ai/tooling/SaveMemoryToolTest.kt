package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.GatewayToolRuntimeContext
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.PromptMode
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveMemoryToolTest {
    @Test
    fun execute_savesSceneStateMemoryDirectly() = runBlocking {
        val tool = SaveMemoryTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-1",
                name = SaveMemoryTool.NAME,
                argumentsJson = """{"content":"他已经承认知道密门位置。","memory_type":"scene_state"}""",
            ),
            context = ToolContext(
                searchRepository = fakeSearchRepository(),
                memoryWriteService = object : MemoryWriteService {
                    override suspend fun saveSceneMemory(
                        toolContext: ToolContext,
                        content: String,
                        importance: Int,
                    ): MemoryWriteResult {
                        assertEquals("他已经承认知道密门位置。", content)
                        return MemoryWriteResult(
                            scopeType = MemoryScopeType.CONVERSATION,
                            deduplicated = false,
                        )
                    }

                    override suspend fun proposePersistentMemory(
                        toolContext: ToolContext,
                        content: String,
                        reason: String,
                        importance: Int,
                    ): PendingMemoryProposal = error("不应调用")

                    override suspend fun approveProposal(proposalId: String) = null

                    override suspend fun rejectProposal(proposalId: String) = Unit
                },
                runtimeContext = GatewayToolRuntimeContext(
                    promptMode = PromptMode.ROLEPLAY,
                    assistant = Assistant(
                        id = "assistant-1",
                        memoryEnabled = true,
                    ),
                    conversation = Conversation(
                        id = "conversation-1",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertEquals("saved", payload["status"].asString)
        assertEquals("conversation", payload["scope"].asString)
    }

    @Test
    fun execute_proposesPersistentMemoryForConfirmation() = runBlocking {
        val tool = SaveMemoryTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-2",
                name = SaveMemoryTool.NAME,
                argumentsJson = """{"content":"她不喜欢被突然逼问。","memory_type":"persistent","reason":"这是稳定偏好"}""",
            ),
            context = ToolContext(
                searchRepository = fakeSearchRepository(),
                memoryWriteService = object : MemoryWriteService {
                    override suspend fun saveSceneMemory(
                        toolContext: ToolContext,
                        content: String,
                        importance: Int,
                    ): MemoryWriteResult = error("不应调用")

                    override suspend fun proposePersistentMemory(
                        toolContext: ToolContext,
                        content: String,
                        reason: String,
                        importance: Int,
                    ): PendingMemoryProposal {
                        return PendingMemoryProposal(
                            id = "proposal-1",
                            conversationId = "conversation-1",
                            assistantId = "assistant-1",
                            scopeType = MemoryScopeType.ASSISTANT,
                            content = content,
                            reason = reason,
                            importance = importance,
                        )
                    }

                    override suspend fun approveProposal(proposalId: String) = null

                    override suspend fun rejectProposal(proposalId: String) = Unit
                },
                runtimeContext = GatewayToolRuntimeContext(
                    promptMode = PromptMode.ROLEPLAY,
                    assistant = Assistant(
                        id = "assistant-1",
                        memoryEnabled = true,
                    ),
                    conversation = Conversation(
                        id = "conversation-1",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertEquals("pending_confirmation", payload["status"].asString)
        assertEquals("proposal-1", payload["proposal_id"].asString)
    }

    @Test
    fun execute_usesDefaultImportanceWhenArgumentTypeInvalid() = runBlocking {
        val tool = SaveMemoryTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-invalid-importance",
                name = SaveMemoryTool.NAME,
                argumentsJson = """{"content":"他已经拿到铜钥匙。","memory_type":"scene_state"}""",
            ),
            context = toolContext(
                memoryWriteService = object : MemoryWriteService {
                    override suspend fun saveSceneMemory(
                        toolContext: ToolContext,
                        content: String,
                        importance: Int,
                    ): MemoryWriteResult {
                        assertEquals(70, importance)
                        return MemoryWriteResult(
                            scopeType = MemoryScopeType.CONVERSATION,
                            deduplicated = false,
                        )
                    }

                    override suspend fun proposePersistentMemory(
                        toolContext: ToolContext,
                        content: String,
                        reason: String,
                        importance: Int,
                    ): PendingMemoryProposal = error("不应调用")

                    override suspend fun approveProposal(proposalId: String) = null

                    override suspend fun rejectProposal(proposalId: String) = Unit
                },
            ),
        )

        assertFalse(result.isError)
    }

    @Test
    fun execute_returnsErrorWhenImportanceTypeInvalid() = runBlocking {
        val tool = SaveMemoryTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-invalid-importance",
                name = SaveMemoryTool.NAME,
                argumentsJson = """{"content":"他已经拿到铜钥匙。","memory_type":"scene_state","importance":{"bad":true}}""",
            ),
            context = toolContext(
                memoryWriteService = object : MemoryWriteService {
                    override suspend fun saveSceneMemory(
                        toolContext: ToolContext,
                        content: String,
                        importance: Int,
                    ): MemoryWriteResult = error("错误参数不应写入")

                    override suspend fun proposePersistentMemory(
                        toolContext: ToolContext,
                        content: String,
                        reason: String,
                        importance: Int,
                    ): PendingMemoryProposal = error("错误参数不应写入")

                    override suspend fun approveProposal(proposalId: String) = null

                    override suspend fun rejectProposal(proposalId: String) = Unit
                },
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertTrue(result.isError)
        assertTrue(payload["error"].asString.contains("重要度"))
    }

    @Test
    fun execute_returnsErrorWhenMemoryTypeTypeInvalid() = runBlocking {
        val tool = SaveMemoryTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-invalid-type",
                name = SaveMemoryTool.NAME,
                argumentsJson = """{"content":"他已经拿到铜钥匙。","memory_type":{"bad":true}}""",
            ),
            context = toolContext(
                memoryWriteService = object : MemoryWriteService {
                    override suspend fun saveSceneMemory(
                        toolContext: ToolContext,
                        content: String,
                        importance: Int,
                    ): MemoryWriteResult = error("错误参数不应写入")

                    override suspend fun proposePersistentMemory(
                        toolContext: ToolContext,
                        content: String,
                        reason: String,
                        importance: Int,
                    ): PendingMemoryProposal = error("错误参数不应写入")

                    override suspend fun approveProposal(proposalId: String) = null

                    override suspend fun rejectProposal(proposalId: String) = Unit
                },
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertTrue(result.isError)
        assertTrue(payload["error"].asString.contains("记忆类型"))
    }

    @Test
    fun execute_returnsErrorWhenArgumentUnknown() = runBlocking {
        val tool = SaveMemoryTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-unknown",
                name = SaveMemoryTool.NAME,
                argumentsJson = """{"content":"他已经拿到铜钥匙。","memory_type":"scene_state","extra":"x"}""",
            ),
            context = toolContext(
                memoryWriteService = object : MemoryWriteService {
                    override suspend fun saveSceneMemory(
                        toolContext: ToolContext,
                        content: String,
                        importance: Int,
                    ): MemoryWriteResult = error("未知参数不应写入")

                    override suspend fun proposePersistentMemory(
                        toolContext: ToolContext,
                        content: String,
                        reason: String,
                        importance: Int,
                    ): PendingMemoryProposal = error("未知参数不应写入")

                    override suspend fun approveProposal(proposalId: String) = null

                    override suspend fun rejectProposal(proposalId: String) = Unit
                },
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertTrue(result.isError)
        assertTrue(payload["error"].asString.contains("不支持的参数"))
    }

    @Test
    fun execute_redactsSecretBeforeWritingMemory() = runBlocking {
        val tool = SaveMemoryTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-secret",
                name = SaveMemoryTool.NAME,
                argumentsJson = """{"content":"Authorization: Bearer sk-secret 她知道密门。","memory_type":"scene_state"}""",
            ),
            context = toolContext(
                memoryWriteService = object : MemoryWriteService {
                    override suspend fun saveSceneMemory(
                        toolContext: ToolContext,
                        content: String,
                        importance: Int,
                    ): MemoryWriteResult {
                        assertFalse(content.contains("sk-secret"))
                        assertTrue(content.contains("[REDACTED]"))
                        return MemoryWriteResult(
                            scopeType = MemoryScopeType.CONVERSATION,
                            deduplicated = false,
                        )
                    }

                    override suspend fun proposePersistentMemory(
                        toolContext: ToolContext,
                        content: String,
                        reason: String,
                        importance: Int,
                    ): PendingMemoryProposal = error("不应调用")

                    override suspend fun approveProposal(proposalId: String) = null

                    override suspend fun rejectProposal(proposalId: String) = Unit
                },
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertFalse(payload["content"].asString.contains("sk-secret"))
        assertTrue(payload["content"].asString.contains("[REDACTED]"))
    }

    @Test
    fun execute_rejectsOverlongMemoryContent() = runBlocking {
        val tool = SaveMemoryTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-long",
                name = SaveMemoryTool.NAME,
                argumentsJson = """{"content":"${"剧情".repeat(400)}","memory_type":"scene_state"}""",
            ),
            context = toolContext(
                memoryWriteService = object : MemoryWriteService {
                    override suspend fun saveSceneMemory(
                        toolContext: ToolContext,
                        content: String,
                        importance: Int,
                    ): MemoryWriteResult = error("过长内容不应写入")

                    override suspend fun proposePersistentMemory(
                        toolContext: ToolContext,
                        content: String,
                        reason: String,
                        importance: Int,
                    ): PendingMemoryProposal = error("过长内容不应写入")

                    override suspend fun approveProposal(proposalId: String) = null

                    override suspend fun rejectProposal(proposalId: String) = Unit
                },
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertTrue(result.isError)
        assertTrue(payload["error"].asString.contains("过长"))
    }

    private fun toolContext(
        memoryWriteService: MemoryWriteService,
    ): ToolContext {
        return ToolContext(
            searchRepository = fakeSearchRepository(),
            memoryWriteService = memoryWriteService,
            runtimeContext = GatewayToolRuntimeContext(
                promptMode = PromptMode.ROLEPLAY,
                assistant = Assistant(
                    id = "assistant-1",
                    memoryEnabled = true,
                ),
                conversation = Conversation(
                    id = "conversation-1",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
    }

    private fun fakeSearchRepository(): com.example.myapplication.data.repository.search.SearchRepository {
        return object : com.example.myapplication.data.repository.search.SearchRepository {
            override suspend fun search(
                source: com.example.myapplication.model.SearchSourceConfig,
                query: String,
                resultCount: Int,
            ): com.example.myapplication.data.repository.search.SearchResult {
                error("测试不应执行搜索")
            }
        }
    }
}
