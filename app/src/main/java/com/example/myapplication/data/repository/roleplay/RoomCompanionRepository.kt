package com.example.myapplication.data.repository.roleplay

import androidx.room.withTransaction
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.chat.MessageEntity
import com.example.myapplication.data.local.companion.*
import com.example.myapplication.data.local.memory.MemorySourceStorage
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import java.util.UUID

class RoomCompanionRepository(
    private val database: ChatDatabase,
    private val archive: MemoryArchiveRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val requestsAllowed: suspend () -> Boolean = { true },
    private val historyAllowed: suspend (AllowedCompanionHistoryScope) -> Boolean = { false },
) : CompanionRepository {
    private val dao = database.companionDao()
    private val messagesDao = database.conversationDao()
    private val sources = MemorySourceStorage(database, now)
    private val eligibility = CompanionSourceStorage(database)
    private val store = RoomConversationStore(database, now)
    private val gson = AppJson.gson
    private val replyParser = com.example.myapplication.roleplay.companion.CompanionReplyParser()

    override suspend fun createEncounter(
        context: MemoryRuntimeContext,
        openingMode: CompanionOpeningMode,
        encounterId: String,
    ): CompanionEncounter = database.withTransaction {
        require(encounterId.isNotBlank() && validContext(context)) { "当前角色或陪伴运行已改变" }
        dao.getEncounter(encounterId)?.let { old ->
            require(old.context() == context && old.openingMode == openingMode.name) { "场次标识已经用于其它内容" }
            return@withTransaction old.domain()
        }
        val direct = openingMode == CompanionOpeningMode.DIRECT
        // 新选择在持久层废弃旧预览，不能让迟到的旧开场夺回新场。
        dao.listEncounters(context.conversationId).filter {
            it.state == "DRAFT" && it.characterId == context.characterId && it.personaKey == context.personaKey
        }.forEach { close(it) }
        if (direct) closeActive(context)
        val entity = CompanionEncounterEntity(
            encounterId, context.conversationId, context.scenarioId, context.runId,
            context.spaceId, context.characterId, context.personaKey, context.generation,
            openingMode.name, if (direct) "ACTIVE" else "DRAFT", 1,
            if (direct) activeKey(context) else null, now(), null,
        )
        dao.insertEncounter(entity)
        entity.domain()
    }

    override suspend fun getEncounter(id: String) = dao.getEncounter(id)?.domain()
    override suspend fun listEncounters(conversationId: String) =
        dao.listEncounters(conversationId).map { it.domain() }

    override suspend fun endEncounter(id: String): Boolean = database.withTransaction {
        val encounter = dao.getEncounter(id) ?: return@withTransaction false
        if (encounter.state == "ENDED") return@withTransaction true
        close(encounter)
        true
    }

    override suspend fun beginTurn(
        encounterId: String,
        kind: CompanionTurnKind,
        input: ChatMessage?,
        descriptor: CompanionRequestDescriptor,
        turnId: String,
    ): CompanionPreparedTurn = database.withTransaction {
        val encounter = dao.getEncounter(encounterId) ?: error("陪伴场次不存在")
        require(validContext(encounter.context()) && turnId.isNotBlank()) { "当前角色或陪伴运行已改变" }
        validateDescriptor(descriptor)
        require(if (kind == CompanionTurnKind.OPENING) encounter.state == "DRAFT" else encounter.state == "ACTIVE") {
            "当前场次尚未接受开场或已经结束"
        }
        dao.getTurn(turnId)?.let { old ->
            val attempt = old.activeAttemptId?.let { dao.getAttempt(it) }
                ?: old.selectedAttemptId?.let { dao.getAttempt(it) } ?: error("回合没有可恢复请求")
            require(
                old.encounterId == encounterId && old.kind == kind.name &&
                        attempt.requestDescriptorJson == gson.toJson(descriptor)
            ) { "回合标识已经用于其它内容" }
            val oldInput = old.inputMessageId?.let { messagesDao.getMessage(it) }
            if (kind == CompanionTurnKind.INPUT) require(
                input != null && oldInput != null &&
                        oldInput.id == input.id && oldInput.content == input.content &&
                        oldInput.partsJson == gson.toJson(normalizeChatMessageParts(input.parts))
            ) { "不能用同一回合替换原输入" }
            return@withTransaction CompanionPreparedTurn(old.domain(), attempt.domain())
        }
        val turns = dao.listTurns(encounterId)
        require(kind != CompanionTurnKind.OPENING || turns.isEmpty()) { "开场预览请沿原回合重新生成" }
        val pending = turns.lastOrNull()?.activeAttemptId?.let { dao.getAttempt(it) }
        require(
            pending?.state !in setOf(
                "NOT_STARTED",
                "REQUESTED",
                "RESULT_READY"
            )
        ) { "请先处理当前回合" }
        val userMessage = when (kind) {
            CompanionTurnKind.INPUT -> {
                require(
                    input != null && input.role == MessageRole.USER &&
                            input.id.isNotBlank() && input.hasSendableContent() && !input.isRecalled &&
                            input.conversationId == encounter.conversationId
                ) { "陪伴输入无效" }
                require(messagesDao.getMessage(input.id) == null) { "输入消息标识已存在" }
                input.copy(
                    status = MessageStatus.COMPLETED,
                    companionTurnId = turnId,
                    companionMemoryState = CompanionMemoryState.WAITING_TURN,
                    companionSourceProof = null,
                    createdAt = input.createdAt.takeIf { it > 0 } ?: now())
            }

            CompanionTurnKind.CONTINUE -> {
                require(input == null) { "继续不包含新的用户言行" }
                ChatMessage(
                    newId(),
                    encounter.conversationId,
                    MessageRole.USER,
                    COMPANION_CONTINUE_DISPLAY_TEXT,
                    createdAt = now(),
                    companionTurnId = turnId,
                    companionMemoryState = CompanionMemoryState.CONTROL
                )
            }

            CompanionTurnKind.OPENING -> {
                require(input == null) { "开场预览不能包含用户已发生的动作" }; null
            }
        }
        val attemptId = newId()
        var turn = CompanionTurnEntity(
            turnId, encounterId, (turns.lastOrNull()?.ordinal ?: 0) + 1, kind.name,
            userMessage?.id, null, null, null, 1, 0, null, attemptId, now(), null,
        )
        dao.insertTurn(turn)
        if (userMessage != null) {
            val saved = sources.writeMessages(
                listOf(with(store) { userMessage.toEntity() }),
                companionWrite = true
            ).single()
            turn = turn.copy(inputRevision = saved.memoryRevision)
            dao.upsertTurn(turn)
            sources.captureCompanionMessages(listOf(saved))
        }
        val attempt = buildAttempt(attemptId, turn, encounter.context(), descriptor)
        dao.insertAttempt(attempt)
        CompanionPreparedTurn(turn.domain(), attempt.domain())
    }

    override suspend fun newAttempt(
        turnId: String,
        descriptor: CompanionRequestDescriptor?,
        allowUncertainRetry: Boolean,
    ): CompanionAttempt? = database.withTransaction {
        val turn = dao.getTurn(turnId) ?: return@withTransaction null
        val encounter = dao.getEncounter(turn.encounterId) ?: return@withTransaction null
        if (!validContext(encounter.context()) || encounter.state == "ENDED" ||
            dao.listTurns(encounter.id).lastOrNull()?.id != turn.id
        ) return@withTransaction null
        val previous = (turn.activeAttemptId ?: turn.selectedAttemptId)?.let { dao.getAttempt(it) }
            ?: return@withTransaction null
        if (previous.state == "REQUESTED" && (previous.leaseUntil
                ?: 0) > now()
        ) return@withTransaction null
        if (previous.requestedAt != null && previous.state in setOf(
                "REQUESTED",
                "UNCERTAIN",
                "CANCELLED"
            ) &&
            !allowUncertainRetry
        ) return@withTransaction null
        if (previous.state == "RESULT_READY" && turn.kind != "OPENING") {
            // 有效完整结果优先免费补存；来源已失效时，明确的新请求可重冻本轮来源。
            if (!allowUncertainRetry ||
                decodeArtifact(previous)?.completionStatus == PromptCompletionStatus.COMPLETED &&
                current(previous, forCommit = true) == null
            ) return@withTransaction null
        }
        if (turn.kind == "INPUT" && turn.inputMessageId?.let { messagesDao.getMessage(it) } == null) return@withTransaction null
        if (turn.committedRevision > 0 && turn.replyMessageId?.let { messagesDao.getMessage(it) } == null) return@withTransaction null
        val nextDescriptor = descriptor ?: decodeDescriptor(previous).let { frozen ->
            // 首次提交会提升 INPUT 来源资格/版本，但正文和逻辑输入仍是同一条。
            frozen.copy(sourceMessages = frozen.sourceMessages.map { ref ->
                if (ref.messageId == turn.inputMessageId && turn.inputRevision != null)
                    ref.copy(revision = turn.inputRevision) else ref
            })
        }
        validateDescriptor(nextDescriptor)
        val id = newId()
        val next = turn.copy(mutationRevision = turn.mutationRevision + 1, activeAttemptId = id)
        if (previous.state != "COMMITTED") dao.upsertAttempt(
            previous.copy(
                state = "CANCELLED", claimToken = null, leaseUntil = null,
            )
        )
        dao.upsertTurn(next)
        val attempt = buildAttempt(id, next, encounter.context(), nextDescriptor)
        dao.insertAttempt(attempt)
        attempt.domain()
    }

    override suspend fun getTurn(id: String) = dao.getTurn(id)?.domain()
    override suspend fun listTurns(encounterId: String) =
        dao.listTurns(encounterId).map { it.domain() }

    override suspend fun getAttempt(id: String) = dao.getAttempt(id)?.domain()

    override suspend fun validateRequest(id: String, owner: String): Boolean =
        database.withTransaction {
            val attempt = dao.getAttempt(id) ?: return@withTransaction false
            attempt.state == "REQUESTED" && attempt.claimToken == owner && current(attempt) == null
        }

    override suspend fun recoverEncounter(encounterId: String): List<CompanionAttempt> =
        database.withTransaction {
            dao.listEncounterAttempts(encounterId).map { attempt ->
                if (attempt.state == "REQUESTED" && (attempt.leaseUntil ?: 0) <= now()) {
                    attempt.copy(state = "UNCERTAIN", leaseUntil = null)
                        .also { dao.upsertAttempt(it) }
                } else attempt
            }.filter {
                it.state in setOf(
                    "NOT_STARTED",
                    "RESULT_READY",
                    "UNCERTAIN",
                    "CANCELLED",
                    "FAILED"
                )
            }
                .map { it.domain() }
        }

    override suspend fun claimAttempt(
        id: String,
        owner: String,
        leaseMillis: Long
    ): CompanionAttempt? =
        database.withTransaction {
            require(owner.isNotBlank() && leaseMillis in 1..3_600_000) { "请求所有者或有效期无效" }
            val attempt = dao.getAttempt(id) ?: return@withTransaction null
            if (attempt.state != "NOT_STARTED" || !requestsAllowed() || current(attempt) != null)
                return@withTransaction null
            val claimed = attempt.copy(
                state = "REQUESTED", claimToken = owner,
                leaseUntil = now() + leaseMillis, requestedAt = now()
            )
            dao.upsertAttempt(claimed)
            claimed.domain()
        }

    override suspend fun listPendingResults(context: MemoryRuntimeContext): List<CompanionPendingResult> =
        database.withTransaction {
            if (!validContext(context, requireOffline = false)) return@withTransaction emptyList()
            dao.listEncounters(context.conversationId).filter {
                it.context() == context && it.state != "DRAFT"
            }.mapNotNull { encounter ->
                val turn = dao.listTurns(encounter.id).lastOrNull() ?: return@mapNotNull null
                val attempt =
                    turn.activeAttemptId?.let { dao.getAttempt(it) } ?: return@mapNotNull null
                if (attempt.state != "RESULT_READY" || attempt.artifactJson == null ||
                    attempt.artifactHash != MemoryArchiveIdentity.fingerprint(attempt.artifactJson) ||
                    current(attempt, forCommit = true) != null
                ) return@mapNotNull null
                val artifact =
                    decodeArtifact(attempt)?.takeIf(::isCompleteArtifact) ?: return@mapNotNull null
                CompanionPendingResult(
                    attempt.id, encounter.id, CompanionTurnKind.valueOf(turn.kind),
                    turn.createdAt, artifact.rawReply
                )
            }
        }

    override suspend fun renewLease(id: String, owner: String, leaseMillis: Long): Boolean =
        database.withTransaction {
            require(leaseMillis in 1..3_600_000)
            val attempt = dao.getAttempt(id) ?: return@withTransaction false
            if (attempt.state != "REQUESTED" || attempt.claimToken != owner || current(attempt) != null)
                return@withTransaction false
            dao.upsertAttempt(attempt.copy(leaseUntil = now() + leaseMillis))
            true
        }

    override suspend fun saveGeneratedArtifact(
        id: String, owner: String, artifact: CompanionGeneratedArtifact,
    ): Boolean = database.withTransaction {
        val attempt = dao.getAttempt(id) ?: return@withTransaction false
        if (!canSaveGeneratedArtifact(attempt, owner)) return@withTransaction false
        require(
            artifact.rawReply.length <= MAX_REPLY_CHARS &&
                    artifact.reasoningContent.length <= MAX_REPLY_CHARS
        ) { "陪伴结果超出本地保存上限" }
        val json = gson.toJson(artifact)
        val hash = MemoryArchiveIdentity.fingerprint(json)
        if (attempt.state == "RESULT_READY") return@withTransaction attempt.artifactHash == hash &&
                attempt.artifactJson == json
        dao.upsertAttempt(
            attempt.copy(
                state = "RESULT_READY",
                receivedAt = now(),
                artifactJson = json,
                artifactHash = hash,
            )
        )
        true
    }

    override suspend fun canSaveGeneratedArtifact(id: String, owner: String): Boolean =
        database.withTransaction {
            val attempt = dao.getAttempt(id) ?: return@withTransaction false
            canSaveGeneratedArtifact(attempt, owner)
        }

    private suspend fun canSaveGeneratedArtifact(
        attempt: CompanionAttemptEntity,
        owner: String
    ): Boolean =
        attempt.claimToken == owner && attempt.state in setOf(
            "REQUESTED",
            "RESULT_READY",
            "UNCERTAIN"
        ) &&
                current(attempt) == null

    override suspend fun commitAttempt(id: String, acceptOpening: Boolean): CompanionCommitResult =
        database.withTransaction {
            val attempt = dao.getAttempt(id)
                ?: return@withTransaction conflict(CompanionConflict.ATTEMPT_CHANGED)
            val turn = dao.getTurn(attempt.turnId) ?: return@withTransaction conflict(
                CompanionConflict.TURN_CHANGED
            )
            if (attempt.state == "COMMITTED") {
                // 后续修订使原回执过期，不能把另一请求或人工新答冒充原提交。
                if (turn.selectedAttemptId != attempt.id ||
                    turn.mutationRevision != attempt.expectedTurnRevision + 1
                ) return@withTransaction conflict(CompanionConflict.TURN_CHANGED)
                return@withTransaction receipt(turn, already = true)
            }
            current(attempt, forCommit = true)?.let { return@withTransaction conflict(it) }
            if (!requestsAllowed()) return@withTransaction conflict(CompanionConflict.CONTEXT_CHANGED)
            if (attempt.state != "RESULT_READY" || attempt.artifactJson == null ||
                attempt.artifactHash != MemoryArchiveIdentity.fingerprint(attempt.artifactJson)
            )
                return@withTransaction conflict(CompanionConflict.INVALID_ARTIFACT)
            val artifact = decodeArtifact(attempt) ?: return@withTransaction conflict(
                CompanionConflict.INVALID_ARTIFACT
            )
            if (!isCompleteArtifact(artifact))
                return@withTransaction conflict(CompanionConflict.INCOMPLETE_RESULT)
            val encounter = dao.getEncounter(turn.encounterId)!!
            if (turn.kind == "OPENING" && !acceptOpening) return@withTransaction conflict(
                CompanionConflict.INVALID_INPUT
            )
            val oldReply = turn.replyMessageId?.let { messagesDao.getMessage(it) }
            val oldInput = turn.inputMessageId?.let { messagesDao.getMessage(it) }
            val descriptor = decodeDescriptor(attempt)
            val reservedReplyId = descriptor.replyMessageId?.takeIf { it.isNotBlank() }
            if (oldReply == null && reservedReplyId != null && messagesDao.getMessage(
                    reservedReplyId
                ) != null
            )
                return@withTransaction conflict(CompanionConflict.SOURCE_CHANGED)
            val reply = ChatMessage(
                id = oldReply?.id ?: reservedReplyId ?: newId(),
                conversationId = encounter.conversationId,
                role = MessageRole.ASSISTANT,
                content = artifact.rawReply,
                parts = listOf(textMessagePart(artifact.rawReply)),
                createdAt = oldReply?.createdAt ?: maxOf(
                    turn.createdAt,
                    (oldInput?.createdAt ?: turn.createdAt) + 1
                ),
                modelName = descriptor.modelName,
                reasoningContent = artifact.reasoningContent,
                speakerId = encounter.characterId,
                roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                roleplayInteractionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
                companionTurnId = turn.id,
                companionMemoryState = CompanionMemoryState.READY,
                memoryRecallRequestId = if (descriptor.historyScope != null) attempt.id else "",
            )
            val input =
                oldInput?.copy(companionMemoryState = if (turn.kind == "CONTINUE") "control" else "ready")
            val write = listOfNotNull(input, with(store) { reply.toEntity() })
            val saved = sources.writeMessages(write, companionWrite = true)
            val savedReply = saved.single { it.role == "ASSISTANT" }
            val savedInput = saved.firstOrNull { it.role == "USER" }
            val committed = turn.copy(
                inputRevision = savedInput?.memoryRevision,
                replyMessageId = savedReply.id,
                replyRevision = savedReply.memoryRevision,
                mutationRevision = turn.mutationRevision + 1,
                committedRevision = turn.committedRevision + 1,
                selectedAttemptId = id,
                activeAttemptId = null,
                editedAt = now(),
            )
            dao.upsertTurn(committed)
            dao.upsertAttempt(
                attempt.copy(
                    state = "COMMITTED",
                    claimToken = null,
                    leaseUntil = null
                )
            )
            if (encounter.state == "DRAFT") {
                closeActive(encounter.context())
                dao.upsertEncounter(
                    encounter.copy(
                        state = "ACTIVE",
                        activeKey = activeKey(encounter.context()),
                        revision = encounter.revision + 1
                    )
                )
            }
            sources.captureCompanionMessages(saved)
            messagesDao.getConversation(encounter.conversationId)?.let {
                messagesDao.upsertConversationEntity(it.copy(updatedAt = now()))
            }
            receipt(committed)
        }

    override suspend fun cancelAttempt(id: String, owner: String, uncertain: Boolean): Boolean =
        database.withTransaction {
            val attempt = dao.getAttempt(id) ?: return@withTransaction false
            if (attempt.state == "COMMITTED" || attempt.claimToken != owner) return@withTransaction false
            dao.upsertAttempt(
                attempt.copy(
                    state = if (uncertain && attempt.requestedAt != null) "UNCERTAIN" else "CANCELLED",
                    claimToken = null, leaseUntil = null,
                )
            )
            true
        }

    override suspend fun editMessage(
        messageId: String,
        expectedRevision: Long,
        content: String
    ): Boolean = editCanonicalMessage(messageId, expectedRevision, content, preserveParts = false)

    private suspend fun editCanonicalMessage(
        messageId: String,
        expectedRevision: Long,
        content: String,
        preserveParts: Boolean,
    ): Boolean =
        database.withTransaction {
            val message = messagesDao.getMessage(messageId) ?: return@withTransaction false
            val turn = dao.getMessageTurn(messageId) ?: return@withTransaction false
            val encounter = dao.getEncounter(turn.encounterId) ?: return@withTransaction false
            if (!validContext(
                    encounter.context(),
                    requireOffline = false
                ) || message.memoryRevision != expectedRevision ||
                message.companionMemoryState == "control" || content.isBlank()
            ) return@withTransaction false
            sources.writeMessages(
                listOf(
                    message.copy(
                        content = content,
                        partsJson = if (preserveParts) editedPartsJson(message.partsJson, content)
                        else if (message.role == "ASSISTANT") gson.toJson(
                            listOf(
                                textMessagePart(content)
                            )
                        ) else "[]",
                    )
                )
            )
            true
        }

    override suspend fun deleteMessage(messageId: String, expectedRevision: Long): Boolean =
        database.withTransaction {
            val message = messagesDao.getMessage(messageId) ?: return@withTransaction false
            val turn = dao.getMessageTurn(messageId) ?: return@withTransaction false
            val encounter = dao.getEncounter(turn.encounterId) ?: return@withTransaction false
            if (!validContext(
                    encounter.context(),
                    requireOffline = false
                ) || message.memoryRevision != expectedRevision
            )
                return@withTransaction false
            sources.invalidateMessage(message.conversationId, messageId, deleted = true)
            messagesDao.deleteMessage(message.conversationId, messageId) == 1
        }

    /** 全批次先校验所见版本，再复用来源失效钩子；中途冲突必须退出事务回滚。 */
    override suspend fun mutateMessages(mutations: List<CompanionMessageMutation>): Boolean {
        if (mutations.isEmpty() || mutations.map { it.source.messageId }.distinct().size != mutations.size) return false
        return try {
            database.withTransaction {
                val originals = mutations.map { mutation ->
                    val message = messagesDao.getMessage(mutation.source.messageId) ?: return@withTransaction false
                    if (message.conversationId != mutation.source.conversationId || message.memoryRevision != mutation.source.revision ||
                        mutation.source.revision <= 0L || mutation.content?.isBlank() == true ||
                        mutation.content != null && message.companionMemoryState == "control"
                    ) return@withTransaction false
                    val turn = dao.getMessageTurn(message.id)
                    if (message.companionTurnId.isNotBlank()) {
                        if (turn?.id != message.companionTurnId || message.companionMemoryState == "legacy") return@withTransaction false
                        val encounter = turn?.let { dao.getEncounter(it.encounterId) } ?: return@withTransaction false
                        if (!validContext(encounter.context(), requireOffline = false)) return@withTransaction false
                    } else {
                        if (turn != null || message.companionMemoryState != "legacy") return@withTransaction false
                        val binding = database.memoryArchiveDao().listBindings(message.conversationId).singleOrNull()
                            ?: return@withTransaction false
                        val context = archive.getRuntimeContext(message.conversationId, binding.characterId) ?: return@withTransaction false
                        if (!validContext(context, requireOffline = false)) return@withTransaction false
                    }
                    message
                }
                mutations.zip(originals).forEach { (mutation, message) ->
                    val content = mutation.content
                    if (content != null) {
                        if (message.companionTurnId.isNotBlank()) {
                            if (!editCanonicalMessage(message.id, mutation.source.revision, content, preserveParts = true)) throw BatchMutationConflict()
                        } else sources.writeMessages(listOf(message.copy(content = content,
                            partsJson = editedPartsJson(message.partsJson, content))))
                    } else {
                        val conversation = store.getConversation(message.conversationId) ?: throw BatchMutationConflict()
                        val deleted = store.deleteMessageWithAssociations(conversation, message.conversationId, message.id)
                        if (deleted is com.example.myapplication.conversation.MessageDeletionResult.NotFound) throw BatchMutationConflict()
                    }
                }
                true
            }
        } catch (_: BatchMutationConflict) {
            false
        }
    }

    private class BatchMutationConflict : RuntimeException()

    /** 原位改文字保留媒体及未知扩展；原单条 API 的旧替换语义不变。 */
    private fun editedPartsJson(raw: String, content: String): String {
        val parsed = com.google.gson.JsonParser.parseString(raw)
        require(parsed.isJsonArray) { "原文媒体结构不受支持，本次编辑未写入" }
        val rewritten = com.google.gson.JsonArray()
        var replacedText = false
        parsed.asJsonArray.forEach { part ->
            val type = part.takeIf { it.isJsonObject }?.asJsonObject?.get("type")
            if (type?.isJsonPrimitive == true && type.asString == "TEXT") {
                if (!replacedText) {
                    val text = part.deepCopy().asJsonObject
                    text.addProperty("text", content)
                    rewritten.add(text)
                    replacedText = true
                }
            } else rewritten.add(part.deepCopy())
        }
        if (replacedText) return gson.toJson(rewritten)
        val withText = com.google.gson.JsonArray()
        withText.add(gson.toJsonTree(textMessagePart(content)))
        rewritten.forEach { withText.add(it) }
        return gson.toJson(withText)
    }

    override suspend fun messages(encounterId: String): List<ChatMessage> {
        val encounter = dao.getEncounter(encounterId) ?: return emptyList()
        val turns = dao.listTurns(encounterId).map { it.id }.toSet()
        return store.listMessages(encounter.conversationId).filter { it.companionTurnId in turns }
    }

    private suspend fun current(
        attempt: CompanionAttemptEntity,
        forCommit: Boolean = false
    ): CompanionConflict? {
        if (attempt.requestFingerprint != MemoryArchiveIdentity.fingerprint(attempt.requestDescriptorJson))
            return CompanionConflict.INVALID_INPUT
        val context = decodeContext(attempt) ?: return CompanionConflict.CONTEXT_CHANGED
        if (!validContext(
                context,
                requireOffline = !forCommit
            )
        ) return CompanionConflict.CONTEXT_CHANGED
        val turn = dao.getTurn(attempt.turnId) ?: return CompanionConflict.TURN_CHANGED
        val encounter =
            dao.getEncounter(turn.encounterId) ?: return CompanionConflict.ENCOUNTER_CHANGED
        if (dao.listTurns(encounter.id)
                .lastOrNull()?.id != turn.id
        ) return CompanionConflict.TURN_CHANGED
        if (encounter.context() != context || encounter.state == "ENDED" &&
            !(forCommit && attempt.state == "RESULT_READY" &&
                    (turn.kind != "OPENING" || turn.committedRevision > 0 && turn.replyMessageId != null))
        )
            return CompanionConflict.ENCOUNTER_CHANGED
        if (turn.activeAttemptId != attempt.id || turn.mutationRevision != attempt.expectedTurnRevision)
            return CompanionConflict.TURN_CHANGED
        val input = turn.inputMessageId?.let { messagesDao.getMessage(it) }
        val reply = turn.replyMessageId?.let { messagesDao.getMessage(it) }
        if (input?.memoryRevision != attempt.expectedInputRevision ||
            reply?.memoryRevision != attempt.expectedReplyRevision
        ) return CompanionConflict.SOURCE_CHANGED
        if (input?.isRecalled == true || reply?.isRecalled == true) return CompanionConflict.SOURCE_CHANGED
        decodeDescriptor(attempt).historyScope?.let { history ->
            if (history.context != context || history.messages != decodeDescriptor(attempt).sourceMessages ||
                !historyAllowed(history)
            ) return CompanionConflict.SOURCE_CHANGED
        }
        for (ref in decodeDescriptor(attempt).sourceMessages) {
            val message =
                messagesDao.getMessage(ref.messageId) ?: return CompanionConflict.SOURCE_CHANGED
            if (message.conversationId != ref.conversationId || message.memoryRevision != ref.revision ||
                message.isRecalled
            ) return CompanionConflict.SOURCE_CHANGED
            val binding =
                database.memoryArchiveDao().getBinding(ref.conversationId, context.characterId)
                    ?: return CompanionConflict.SOURCE_CHANGED
            if (binding.spaceId != context.spaceId || binding.personaKey != context.personaKey ||
                database.memoryArchiveDao().listBindings(ref.conversationId).size != 1
            )
                return CompanionConflict.SOURCE_CHANGED
            if (message.id != input?.id && eligibility.eligibility(message) != CompanionSourceEligibility.TEXT_ALLOWED)
                return CompanionConflict.SOURCE_CHANGED
        }
        return null
    }

    private suspend fun validContext(
        context: MemoryRuntimeContext,
        requireOffline: Boolean = true
    ): Boolean {
        if (context.characterId.isBlank() || context.conversationId.isBlank() || context.runId.isBlank()) return false
        if (archive.getRuntimeContext(
                context.conversationId,
                context.characterId
            ) != context
        ) return false
        val scenario = database.roleplayDao().getScenario(context.scenarioId) ?: return false
        return scenario.assistantId == context.characterId && scenario.chatType == "single" &&
                (!requireOffline || scenario.interactionMode == RoleplayInteractionMode.OFFLINE_DIALOGUE.storageValue &&
                        !scenario.longformModeEnabled)
    }

    private suspend fun receipt(
        turn: CompanionTurnEntity,
        already: Boolean = false
    ): CompanionCommitResult {
        val reply = turn.replyMessageId?.let { messagesDao.getMessage(it) }
            ?: return conflict(CompanionConflict.SOURCE_CHANGED)
        val input = turn.inputMessageId?.let { messagesDao.getMessage(it) }
        return CompanionCommitResult.Committed(
            turn.domain(), input?.let { with(store) { it.toDomain() } },
            with(store) { reply.toDomain() }, already,
        )
    }

    private suspend fun closeActive(context: MemoryRuntimeContext) {
        dao.getActiveEncounter(activeKey(context))?.let { close(it) }
    }

    private suspend fun close(encounter: CompanionEncounterEntity) {
        dao.listEncounterAttempts(encounter.id)
            .filter { it.state in setOf("NOT_STARTED", "REQUESTED") }
            .forEach {
                dao.upsertAttempt(
                    it.copy(
                        state = if (it.requestedAt == null) "CANCELLED" else "UNCERTAIN",
                        claimToken = null, leaseUntil = null,
                    )
                )
            }
        dao.upsertEncounter(
            encounter.copy(
                state = "ENDED",
                activeKey = null,
                revision = encounter.revision + 1,
                endedAt = now(),
            )
        )
    }

    private fun activeKey(context: MemoryRuntimeContext) = MemoryArchiveIdentity.id(
        "companion-active", context.conversationId, context.characterId, context.personaKey,
    )

    private fun validateDescriptor(descriptor: CompanionRequestDescriptor) {
        require(
            descriptor.protocolVersion > 0 && descriptor.settingsRevision >= 0 &&
                    descriptor.sourceMessages.size <= 10_000 &&
                    descriptor.sourceMessages.map { it.messageId }
                        .distinct().size == descriptor.sourceMessages.size &&
                    descriptor.sourceMessages.all { it.messageId.isNotBlank() && it.conversationId.isNotBlank() && it.revision > 0 }) {
            "陪伴请求版本或来源范围无效"
        }
    }

    private fun isCompleteArtifact(artifact: CompanionGeneratedArtifact): Boolean =
        artifact.completionStatus == PromptCompletionStatus.COMPLETED && artifact.rawReply.isNotBlank() &&
                !artifact.rawReply.trimStart().startsWith("<html", ignoreCase = true) &&
                !artifact.rawReply.trimStart().startsWith("<!doctype html", ignoreCase = true) &&
                replyParser.hasReadableBody(artifact.rawReply)

    private fun buildAttempt(
        id: String,
        turn: CompanionTurnEntity,
        context: MemoryRuntimeContext,
        descriptor: CompanionRequestDescriptor,
    ): CompanionAttemptEntity {
        val requestJson = gson.toJson(descriptor)
        return CompanionAttemptEntity(
            id, turn.id, turn.mutationRevision, turn.inputRevision, turn.replyRevision,
            gson.toJson(context), requestJson, MemoryArchiveIdentity.fingerprint(requestJson),
            "NOT_STARTED", null, null, null, null, null, null,
        )
    }

    private fun decodeContext(value: CompanionAttemptEntity): MemoryRuntimeContext? =
        runCatching {
            gson.fromJson(
                value.frozenRuntimeJson,
                MemoryRuntimeContext::class.java
            )
        }.getOrNull()

    private fun decodeDescriptor(value: CompanionAttemptEntity): CompanionRequestDescriptor =
        com.example.myapplication.data.local.CompanionDescriptorCodec.decode(value.requestDescriptorJson)

    private fun decodeArtifact(value: CompanionAttemptEntity): CompanionGeneratedArtifact? =
        value.artifactJson?.let {
            runCatching {
                gson.fromJson(
                    it,
                    CompanionGeneratedArtifact::class.java
                )
            }.getOrNull()
        }

    private fun CompanionEncounterEntity.context() =
        MemoryRuntimeContext(
            characterId,
            spaceId,
            conversationId,
            scenarioId,
            runId,
            personaKey,
            generation
        )

    private fun CompanionEncounterEntity.domain() = CompanionEncounter(
        id,
        context(),
        CompanionOpeningMode.valueOf(openingMode),
        CompanionEncounterState.valueOf(state),
        revision,
        createdAt,
        endedAt,
    )

    private fun CompanionTurnEntity.domain() = CompanionTurn(
        id,
        encounterId,
        ordinal,
        CompanionTurnKind.valueOf(kind),
        inputMessageId,
        replyMessageId,
        inputRevision,
        replyRevision,
        mutationRevision,
        committedRevision,
        selectedAttemptId,
        activeAttemptId,
        createdAt,
    )

    private fun CompanionAttemptEntity.domain() = CompanionAttempt(
        id,
        turnId,
        requireNotNull(decodeContext(this)) { "请求运行快照损坏" },
        decodeDescriptor(this),
        CompanionAttemptState.valueOf(state),
        expectedTurnRevision,
        expectedInputRevision,
        expectedReplyRevision,
        claimToken,
        leaseUntil,
        requestedAt,
        receivedAt,
        decodeArtifact(this),
        artifactHash,
    )

    private fun conflict(reason: CompanionConflict) = CompanionCommitResult.Conflict(reason)

    companion object {
        private const val MAX_REPLY_CHARS = 2_000_000
    }
}
