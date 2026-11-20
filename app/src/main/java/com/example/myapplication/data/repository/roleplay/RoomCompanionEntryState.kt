package com.example.myapplication.data.repository.roleplay

import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.companion.CompanionAttemptEntity
import com.example.myapplication.model.CompanionEntryOrigin
import com.example.myapplication.model.CompanionRequestDescriptor
import com.example.myapplication.model.CompanionSessionSelection
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.system.json.AppJson

/** 由 RoleplayRepository 的场景事务调用。入口元数据随原会话级联清理，不发布为消息。 */
internal class RoomCompanionEntryState(
    database: ChatDatabase,
    private val companion: CompanionRepository,
) {
    private val dao = database.companionDao()

    suspend fun read(entryId: String): CompanionSessionSelection? {
        val (attempt, descriptor) = marker(entryId) ?: return null
        val root = companion.getEncounter(entryId) ?: return null
        val selected = companion.getEncounter(descriptor.entrySelectionId ?: entryId) ?: return null
        val origin = descriptor.entryOrigin ?: return null
        if (attempt.turnId != "$entryId:opening" || selected.context != root.context ||
            !origin.matches(root.context)
        ) return null
        return CompanionSessionSelection(selected, entryId, origin)
    }

    suspend fun find(encounterId: String, conversationId: String): CompanionSessionSelection? =
        companion.listEncounters(conversationId).mapNotNull { read(it.id) }
            .filter { it.encounter.id == encounterId }.singleOrNull()

    suspend fun select(entryId: String, expectedEncounterId: String, selectedEncounterId: String) {
        val (attempt, descriptor) = requireNotNull(marker(entryId)) { "入口准备记录已失效" }
        check(
            (descriptor.entrySelectionId ?: entryId) == expectedEncounterId
        ) { "本次开场已在别处切换" }
        check(attempt.requestedAt == null && attempt.artifactJson == null) { "不能改写已请求的开场描述" }
        val json = AppJson.gson.toJson(descriptor.copy(entrySelectionId = selectedEncounterId))
        dao.upsertAttempt(
            attempt.copy(
                requestDescriptorJson = json,
                requestFingerprint = MemoryArchiveIdentity.fingerprint(json)
            )
        )
    }

    private suspend fun marker(entryId: String): Pair<CompanionAttemptEntity, CompanionRequestDescriptor>? {
        val turn = dao.getTurn("$entryId:opening") ?: return null
        if (turn.encounterId != entryId || turn.kind != "OPENING") return null
        return dao.listAttempts(turn.id).mapNotNull { attempt ->
            val descriptor = runCatching {
                AppJson.gson.fromJson(
                    attempt.requestDescriptorJson,
                    CompanionRequestDescriptor::class.java
                )
            }.getOrNull() ?: return@mapNotNull null
            // expectedTurnRevision=1 兼容早期未带 selection 的无请求准备记录。
            if (attempt.requestedAt == null && attempt.artifactJson == null &&
                descriptor.entryOrigin != null &&
                (descriptor.entrySelectionId != null || attempt.expectedTurnRevision == 1L)
            )
                attempt to descriptor else null
        }.singleOrNull()
    }

    private fun CompanionEntryOrigin.matches(context: MemoryRuntimeContext): Boolean =
        scenarioId == context.scenarioId && conversationId == context.conversationId &&
                runId == context.runId && characterId == context.characterId && personaKey == context.personaKey
}
