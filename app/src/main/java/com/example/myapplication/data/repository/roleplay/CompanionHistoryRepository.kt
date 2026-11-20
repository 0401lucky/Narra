package com.example.myapplication.data.repository.roleplay

import com.example.myapplication.model.AllowedCompanionHistoryScope
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.CompanionHistoryCursor
import com.example.myapplication.model.CompanionHistorySelection
import com.example.myapplication.model.MemoryRuntimeContext

interface CompanionHistoryRepository {
    suspend fun freeze(
        context: MemoryRuntimeContext,
        cutoff: CompanionHistoryCursor,
        selection: CompanionHistorySelection,
        originalScope: AllowedCompanionHistoryScope? = null,
    ): AllowedCompanionHistoryScope

    suspend fun read(scope: AllowedCompanionHistoryScope): List<ChatMessage>
    suspend fun validate(scope: AllowedCompanionHistoryScope): Boolean
}
