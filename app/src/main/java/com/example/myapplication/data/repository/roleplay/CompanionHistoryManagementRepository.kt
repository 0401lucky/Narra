package com.example.myapplication.data.repository.roleplay

import com.example.myapplication.model.*
import kotlinx.coroutines.flow.Flow

interface CompanionHistoryManagementRepository {
    suspend fun authorize(
        scenarioId: String,
        characterId: String,
        archiveSpaceId: String? = null,
        archiveRunId: String? = null,
    ): CompanionReadingScope?

    suspend fun archiveChoices(scope: CompanionReadingScope): List<CompanionHistoryArchiveChoice>
    suspend fun refreshScope(scope: CompanionReadingScope): CompanionReadingScope?
    suspend fun page(
        scope: CompanionReadingScope,
        query: CompanionHistoryQuery,
        before: CompanionReadingCursor? = null,
        limit: Int = 50,
    ): CompanionHistoryPage

    suspend fun readAll(
        scope: CompanionReadingScope,
        query: CompanionHistoryQuery
    ): List<CompanionReadingRecord>

    suspend fun edit(
        scope: CompanionReadingScope,
        edits: List<CompanionHistoryEdit>
    ): CompanionHistoryMutationResult

    suspend fun delete(
        scope: CompanionReadingScope,
        sources: List<CompanionMessageRevision>
    ): CompanionHistoryMutationResult

    fun observeChanges(scope: CompanionReadingScope): Flow<Unit>
}
