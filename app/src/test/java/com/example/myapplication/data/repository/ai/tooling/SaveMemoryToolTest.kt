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
