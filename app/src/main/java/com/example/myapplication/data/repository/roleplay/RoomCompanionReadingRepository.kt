package com.example.myapplication.data.repository.roleplay

import androidx.room.withTransaction
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.chat.MessageEntity
import com.example.myapplication.data.local.companion.CompanionTurnEntity
import com.example.myapplication.data.local.memory.MemoryConversationBindingEntity
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionReplyProjector
import com.example.myapplication.system.json.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 独立只读用例；按角色/身份/空间/运行及实际来源过滤后再执行 keyset。 */
class RoomCompanionReadingRepository(
    private val database: ChatDatabase,
    private val archive: MemoryArchiveRepository,
    private val scenarios: RoleplayRepository? = null,
) : CompanionReadingRepository {
    private val dao = database.conversationDao()
    private val store = RoomConversationStore(database)

    override suspend fun authorize(
        scenarioId: String,
        characterId: String
    ): CompanionReadingScope? = database.withTransaction {
        val scene = database.roleplayDao().getScenario(scenarioId)
        if (scene?.chatType != "single" || scene.assistantId != characterId) return@withTransaction null
        val session =
            database.roleplayDao().getSessionByScenario(scenarioId) ?: return@withTransaction null
        val context = archive.getRuntimeContext(session.conversationId, characterId)
            ?: return@withTransaction null
        if (context.scenarioId != scenarioId || context.runId != session.runId) return@withTransaction null
        allowed(context)
    }

    override suspend fun refreshScope(scope: CompanionReadingScope): CompanionReadingScope? =
        database.withTransaction {
            val origin = scope.authorizationContext ?: scope.context
            if (archive.getRuntimeContext(
                    origin.conversationId,
                    origin.characterId
                ) != origin || scope.readOnly != (scope.authorizationContext != null)
            )
                return@withTransaction null
            val current = if (scope.readOnly) {
                archivedAllowed(origin, scope.context.spaceId, scope.context.runId)
                    ?.takeIf { it.context == scope.context } ?: return@withTransaction null
            } else allowed(scope.context)
            if (scope.bindings.map { it.conversationId }.distinct().size != scope.bindings.size) return@withTransaction null
            if (scope.bindings.any { old ->
                    current.bindings.none {
                        it.conversationId == old.conversationId &&
                                it.scenarioId == old.scenarioId && it.runId == old.runId &&
                                it.characterId == old.characterId
                    }
                }) return@withTransaction null
            val ids = scope.bindings.map { it.conversationId }.toSet()
            current.copy(
                bindings = current.bindings.filter { it.conversationId in ids },
                encounterIds = current.encounterIds.filter { id ->
                    database.companionDao().getEncounter(id)?.conversationId in ids
                }.toSet()
            )
        }

    override suspend fun page(
        scope: CompanionReadingScope,
        before: CompanionReadingCursor?,
        limit: Int
    ): CompanionReadingPage =
        database.withTransaction {
            require(limit in 1..200) { "阅读分页大小无效" }
            val refreshed = checkNotNull(refreshScope(scope)) { "阅读范围已改变，请重新打开" }
            val records = scan(refreshed, before, null, limit + 1)
            val hasOlder = records.size > limit
            val retained = records.take(limit).asReversed()
            CompanionReadingPage(retained, retained.firstOrNull()?.cursor, hasOlder)
        }

    override suspend fun readWindow(
        scope: CompanionReadingScope,
        oldestInclusive: CompanionReadingCursor
    ): List<CompanionReadingRecord> =
        database.withTransaction {
            val refreshed = checkNotNull(refreshScope(scope)) { "阅读范围已改变，请重新打开" }
            scan(refreshed, null, oldestInclusive, Int.MAX_VALUE).asReversed()
        }

    override suspend fun readAll(scope: CompanionReadingScope): List<CompanionReadingRecord> =
        database.withTransaction {
            val refreshed = checkNotNull(refreshScope(scope)) { "阅读范围已改变，请重新打开" }
            scan(refreshed, null, null, Int.MAX_VALUE).asReversed()
        }

    override suspend fun readCanonicalAll(scope: CompanionReadingScope): List<CompanionReadingRecord> =
        database.withTransaction {
            val refreshed = checkNotNull(refreshScope(scope)) { "原文范围已改变，请重新打开" }
            scan(refreshed, null, null, Int.MAX_VALUE, includePendingInputs = true).asReversed()
        }

    override suspend fun archiveChoices(scope: CompanionReadingScope): List<CompanionHistoryArchiveChoice> =
        database.withTransaction {
            checkNotNull(refreshScope(scope)) { "阅读范围已改变，请重新打开" }
            val origin = scope.authorizationContext ?: scope.context
            archive.listSpaces(origin.characterId).filter { it.status != MemorySpaceStatus.DELETED }
                .flatMap { space ->
                    database.memoryArchiveDao().listSpaceBindings(space.id)
                        .filter { isArchiveBinding(it, origin) }.groupBy { it.runId }
                        .map { (runId, bindings) ->
                            val titles = bindings.mapNotNull { database.roleplayDao().getScenario(it.scenarioId)?.title }
                                .filter(String::isNotBlank).distinct()
                            CompanionHistoryArchiveChoice(space.id, runId, titles.joinToString(" / ").ifBlank { "只读旧档" })
                        }
                }.distinctBy { it.spaceId to it.runId }
        }

    override suspend fun authorizeArchive(
        scope: CompanionReadingScope,
        spaceId: String,
        runId: String,
    ): CompanionReadingScope? = database.withTransaction {
        val origin = scope.authorizationContext ?: scope.context
        if (refreshScope(scope) == null || runId.isBlank()) return@withTransaction null
        archivedAllowed(origin, spaceId, runId)
    }

    override fun observeChanges(scope: CompanionReadingScope): Flow<Unit> =
        dao.observeCompanionReadingChanges(scope.bindings.map { it.conversationId }).map { Unit }

    private suspend fun allowed(context: MemoryRuntimeContext): CompanionReadingScope {
        val current =
            database.memoryArchiveDao().getBinding(context.conversationId, context.characterId)
        val continuous = current?.purpose == MemoryPurpose.CONTINUOUS.storageValue
        val bindings =
            database.memoryArchiveDao().listSpaceBindings(context.spaceId).filter { binding ->
                binding.characterId == context.characterId && binding.personaKey == context.personaKey &&
                        (if (continuous) binding.purpose == MemoryPurpose.CONTINUOUS.storageValue
                        else binding.conversationId == context.conversationId && binding.runId == context.runId && binding.purpose == current?.purpose) &&
                        database.memoryArchiveDao().listBindings(binding.conversationId).size == 1
            }
        return buildScope(context, bindings)
    }

    private suspend fun isArchiveBinding(
        binding: MemoryConversationBindingEntity,
        origin: MemoryRuntimeContext,
    ): Boolean {
        val space = database.memoryArchiveDao().getSpace(binding.spaceId) ?: return false
        val scene = database.roleplayDao().getScenario(binding.scenarioId) ?: return false
        return space.status in setOf("ACTIVE", "ARCHIVED") && (space.status == "ARCHIVED" || binding.archived) &&
                space.characterId == origin.characterId && binding.characterId == origin.characterId &&
                binding.personaKey == origin.personaKey && binding.runId.isNotBlank() &&
                binding.purpose == space.purpose &&
                binding.purpose in MemoryPurpose.entries.map { it.storageValue } &&
                scene.chatType == "single" && scene.assistantId == origin.characterId &&
                database.memoryArchiveDao().listBindings(binding.conversationId).size == 1
    }

    private suspend fun archivedAllowed(
        origin: MemoryRuntimeContext,
        spaceId: String,
        runId: String,
    ): CompanionReadingScope? {
        val space = database.memoryArchiveDao().getSpace(spaceId) ?: return null
        val bindings = database.memoryArchiveDao().listSpaceBindings(spaceId)
            .filter { it.runId == runId && isArchiveBinding(it, origin) }
        val first = bindings.firstOrNull() ?: return null
        if (bindings.any { it.purpose != first.purpose }) return null
        val context = MemoryRuntimeContext(
            origin.characterId, spaceId, first.conversationId, first.scenarioId, runId,
            origin.personaKey, space.generation,
        )
        return buildScope(context, bindings).copy(readOnly = true, authorizationContext = origin)
    }

    private suspend fun buildScope(
        context: MemoryRuntimeContext,
        values: List<MemoryConversationBindingEntity>,
    ): CompanionReadingScope {
        val bindings = values.mapNotNull { binding ->
                val scene = database.roleplayDao().getScenario(binding.scenarioId)
                    ?.takeIf { it.chatType == "single" && it.assistantId == context.characterId }
                    ?: return@mapNotNull null
                CompanionReadingBinding(
                    binding.conversationId, binding.scenarioId, binding.runId, scene.title,
                    context.characterId, scenarios?.getScenario(binding.scenarioId),
                    readOnly = binding.archived || archive.getRuntimeContext(binding.conversationId, context.characterId) == null,
                )
            }
        val encounters = bindings.flatMap { binding ->
            database.companionDao().listEncounters(binding.conversationId).filter {
                it.scenarioId == binding.scenarioId && it.runId == binding.runId && it.spaceId == context.spaceId &&
                        it.characterId == context.characterId && it.personaKey == context.personaKey && it.state in setOf("ACTIVE", "ENDED")
            }.map { it.id }
        }.toSet()
        return CompanionReadingScope(context, bindings, encounters)
    }

    private suspend fun scan(
        scope: CompanionReadingScope,
        before: CompanionReadingCursor?,
        from: CompanionReadingCursor?,
        target: Int,
        includePendingInputs: Boolean = false,
    ): List<CompanionReadingRecord> {
        if (scope.bindings.isEmpty()) return emptyList()
        val bindingMap = scope.bindings.associateBy { it.conversationId }
        var cursor = before
        val result = mutableListOf<CompanionReadingRecord>()
        val visibleToken = "%" + AppJson.gson.toJson(scope.context.characterId)
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        while (result.size < target) {
            val batch = dao.listCompanionReadingPage(
                scope.bindings.map { it.conversationId },
                scope.bindings.map { it.runId }.distinct(),
                scope.encounterIds.toList(),
                scope.context.spaceId,
                scope.context.characterId,
                scope.context.personaKey,
                database.memoryArchiveDao().getBinding(
                    scope.context.conversationId,
                    scope.context.characterId
                )?.purpose.orEmpty(),
                visibleToken,
                cursor?.createdAt,
                cursor?.messageId,
                from?.createdAt,
                from?.messageId,
                80,
                includePendingInputs,
            )
            if (batch.isEmpty()) break
            for (row in batch) {
                val binding = bindingMap[row.conversationId] ?: continue
                val turn = row.companionTurnId.takeIf(String::isNotBlank)
                    ?.let { database.companionDao().getTurn(it) }
                if (row.companionTurnId.isNotBlank() && turn?.kind !in CompanionTurnKind.entries.map { it.name }) continue
                val pending = includePendingInputs && isPendingInput(row, turn, binding, scope)
                val visible =
                    database.memoryArchiveDao().listMessageSources(row.conversationId, row.id)
                        .any { source ->
                            source.kind == "MESSAGE" && source.spaceId == scope.context.spaceId &&
                                    source.characterId == scope.context.characterId && source.personaKey == scope.context.personaKey &&
                                    ((source.messageRevision == row.memoryRevision &&
                                        (source.status == "AVAILABLE" || source.status == "UNKNOWN" && row.companionMemoryState == "control")) ||
                                        (pending && source.speakerRole == "USER" && source.messageRevision in 1L..row.memoryRevision &&
                                            source.status in setOf("UNKNOWN", "CHANGED", "AVAILABLE"))) &&
                                    runCatching {
                                        val characters = AppJson.gson.fromJson(
                                            source.visibleCharacterIdsJson,
                                            Array<String>::class.java
                                        )
                                        characters?.contains(scope.context.characterId) == true &&
                                                (!pending || characters.toSet() == setOf(scope.context.characterId))
                                    }.getOrDefault(false)
                        }
                if (!visible) continue
                val message = with(store) { row.toDomain() }
                if (message.companionSourceEligibility() == CompanionSourceEligibility.UNAVAILABLE && !pending) continue
                if (message.role == MessageRole.ASSISTANT && message.companionTurnId.isNotBlank() &&
                    CompanionReplyProjector.project(message)?.beats.isNullOrEmpty()
                ) continue
                result += CompanionReadingRecord(
                    message, binding, turn?.encounterId,
                    turn?.kind?.let { kind -> CompanionTurnKind.entries.firstOrNull { it.name == kind } },
                    readerEligible = !pending,
                )
                if (result.size == target) break
            }
            val last = batch.last()
            cursor = CompanionReadingCursor(last.createdAt, last.id)
            if (batch.size < 80) break
        }
        return result.distinctBy { it.message.id }
    }

    private suspend fun isPendingInput(
        message: MessageEntity,
        turn: CompanionTurnEntity?,
        binding: CompanionReadingBinding,
        scope: CompanionReadingScope,
    ): Boolean {
        if (turn == null || message.role != "USER" || turn.id != message.companionTurnId ||
            turn.inputMessageId != message.id || turn.inputRevision != message.memoryRevision ||
            turn.committedRevision != 0L || turn.selectedAttemptId != null ||
            turn.replyMessageId != null || turn.replyRevision != null ||
            !(turn.kind == "INPUT" && message.companionMemoryState == "waiting_turn" ||
                turn.kind == "CONTINUE" && message.companionMemoryState == "control")
        ) return false
        val encounter = database.companionDao().getEncounter(turn.encounterId) ?: return false
        return encounter.id in scope.encounterIds && encounter.state in setOf("ACTIVE", "ENDED") &&
                encounter.conversationId == binding.conversationId && encounter.scenarioId == binding.scenarioId &&
                encounter.runId == binding.runId && encounter.spaceId == scope.context.spaceId &&
                encounter.characterId == scope.context.characterId && encounter.personaKey == scope.context.personaKey
    }
}
