package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.EmptyConversationSummaryRepository
import com.example.myapplication.data.repository.context.EmptyMemoryRepository
import com.example.myapplication.data.repository.context.EmptyWorldBookRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.model.GatewayToolRuntimeContext
import com.example.myapplication.model.SearchSourceConfig

data class SearchToolConfig(
    val source: SearchSourceConfig,
    val resultCount: Int,
)

data class ToolContext(
    val searchRepository: SearchRepository,
    val memoryRepository: MemoryRepository = EmptyMemoryRepository,
    val worldBookRepository: WorldBookRepository = EmptyWorldBookRepository,
    val conversationSummaryRepository: ConversationSummaryRepository = EmptyConversationSummaryRepository,
    val memoryWriteService: MemoryWriteService = NoOpMemoryWriteService,
    val runtimeContext: GatewayToolRuntimeContext? = null,
    val searchToolConfig: SearchToolConfig? = null,
)
