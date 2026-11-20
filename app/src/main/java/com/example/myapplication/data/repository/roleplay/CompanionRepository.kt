package com.example.myapplication.data.repository.roleplay

import com.example.myapplication.model.*
import java.util.UUID

interface CompanionRepository {
    suspend fun createEncounter(
        context: MemoryRuntimeContext,
        openingMode: CompanionOpeningMode,
        encounterId: String = UUID.randomUUID().toString(),
    ): CompanionEncounter

    suspend fun getEncounter(id: String): CompanionEncounter?
    suspend fun listEncounters(conversationId: String): List<CompanionEncounter>
    suspend fun endEncounter(id: String): Boolean
    suspend fun beginTurn(
        encounterId: String,
        kind: CompanionTurnKind,
        input: ChatMessage? = null,
        descriptor: CompanionRequestDescriptor = CompanionRequestDescriptor(),
        turnId: String = UUID.randomUUID().toString(),
    ): CompanionPreparedTurn

    suspend fun newAttempt(
        turnId: String,
        descriptor: CompanionRequestDescriptor? = null,
        allowUncertainRetry: Boolean = false,
    ): CompanionAttempt?

    suspend fun getTurn(id: String): CompanionTurn?
    suspend fun listTurns(encounterId: String): List<CompanionTurn> = emptyList()
    suspend fun getAttempt(id: String): CompanionAttempt?
    suspend fun recoverEncounter(encounterId: String): List<CompanionAttempt>
    suspend fun listPendingResults(context: MemoryRuntimeContext): List<CompanionPendingResult> =
        emptyList()

    suspend fun claimAttempt(
        id: String,
        owner: String,
        leaseMillis: Long = 120_000
    ): CompanionAttempt?

    suspend fun renewLease(id: String, owner: String, leaseMillis: Long = 120_000): Boolean
    suspend fun validateRequest(id: String, owner: String): Boolean = false
    suspend fun canSaveGeneratedArtifact(id: String, owner: String): Boolean = false
    suspend fun saveGeneratedArtifact(
        id: String,
        owner: String,
        artifact: CompanionGeneratedArtifact
    ): Boolean

    suspend fun commitAttempt(id: String, acceptOpening: Boolean = false): CompanionCommitResult
    suspend fun cancelAttempt(id: String, owner: String, uncertain: Boolean = false): Boolean
    suspend fun editMessage(messageId: String, expectedRevision: Long, content: String): Boolean
    suspend fun deleteMessage(messageId: String, expectedRevision: Long): Boolean
    suspend fun mutateMessages(mutations: List<CompanionMessageMutation>): Boolean = false
    suspend fun messages(encounterId: String): List<ChatMessage>
}
