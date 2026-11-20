package com.example.myapplication.data.repository.roleplay

import androidx.room.withTransaction
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.companion.CompanionSourceStorage
import com.example.myapplication.data.local.companion.CompanionVoiceFavoriteEntity
import com.example.myapplication.data.local.companion.CompanionVoiceResourceEntity
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionReplyParser
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.logFailure
import java.util.UUID
import java.net.URI
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomCompanionVoiceRepository(
    private val database: ChatDatabase,
    private val publisher: CompanionVoiceFilePublisher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val beforeAttachment: suspend (CompanionVoiceAudio) -> Unit = {},
) : CompanionVoiceRepository {
    private val dao = database.companionVoiceDao()
    private val sourceStorage = CompanionSourceStorage(database)
    private val mutex = Mutex()
    private val pins = mutableMapOf<String, MutableSet<String>>()
    private val cancelledAudioIds = mutableSetOf<String>()

    override suspend fun lookup(key: SpeechRequestKey, ownerToken: String): CompanionAudioReceipt? =
        mutex.withLock {
            requireKey(key)
            val receipt = lookupLocked(key) ?: return@withLock null
            pin(ownerToken, receipt.audioId)
            receipt
        }

    override suspend fun claim(
        key: SpeechRequestKey,
        ownerToken: String,
        explicitRegeneration: Boolean,
    ): CompanionAudioClaimResult = mutex.withLock {
        requireKey(key)
        require(ownerToken.isNotBlank()) { "语音任务所有者无效" }
        lookupLocked(key)?.let { receipt ->
            pin(ownerToken, receipt.audioId)
            return@withLock CompanionAudioClaimResult.Existing(receipt)
        }
        database.withTransaction {
            val existing = dao.resourceForKey(key.value)
            if (existing != null) {
                if (pins.values.any { existing.id in it } ||
                    (existing.claimToken != null && (existing.leaseUntil ?: 0) > clock())) {
                    return@withTransaction CompanionAudioClaimResult.Busy
                }
                if (!explicitRegeneration) return@withTransaction CompanionAudioClaimResult.NeedsExplicitRegeneration
                // 旧收藏仍引用原音频身份；显式重生成另建资源，不覆盖历史产物。
                if (dao.favoriteReferences(existing.id) > 0) {
                    dao.upsertResource(existing.copy(cacheKey = null))
                } else {
                    publisher.delete(existing.id)
                    dao.deleteResource(existing.id)
                }
            }
            val id = UUID.randomUUID().toString()
            val now = clock()
            dao.upsertResource(
                CompanionVoiceResourceEntity(
                    id, key.value, key.value, "PENDING", ownerToken, now + CLAIM_LEASE_MILLIS,
                    "", "", "", 0, "", now, now,
                )
            )
            pin(ownerToken, id)
            CompanionAudioClaimResult.Granted(CompanionAudioClaim(id, key, ownerToken))
        }
    }

    override suspend fun publish(
        claim: CompanionAudioClaim,
        b64Audio: String,
        clip: CompanionVoiceClip,
        isCurrent: suspend () -> Boolean,
    ): CompanionVoiceAudio {
        validateClip(clip)
        checkCurrent(isCurrent)
        mutex.withLock {
            database.withTransaction {
                val row = requireNotNull(dao.resource(claim.audioId)) { "语音生成回执已失效" }
                requireNotCancelled(claim.audioId, row)
                require(row.requestKey == claim.key.value && row.state == "PENDING") { "语音生成回执不匹配" }
                require(
                    row.claimToken == claim.ownerToken || row.claimToken == null || (row.leaseUntil
                        ?: 0) <= clock()
                ) {
                    "语音资源正由其它任务保存"
                }
                // 内存产物的显式补保存可重新取得存储权；这一步不触发网络。
                dao.upsertResource(
                    row.copy(
                        claimToken = claim.ownerToken,
                        leaseUntil = clock() + CLAIM_LEASE_MILLIS
                    )
                )
                pin(claim.ownerToken, row.id)
            }
        }
        var cancellationHandled = false
        try {
            val audio = publisher.publish(claim, b64Audio, clip, isCurrent)
            return mutex.withLock {
                try {
                    checkCurrent(isCurrent)
                    val row = dao.resource(claim.audioId)
                    if (row?.claimToken != claim.ownerToken || row.requestKey != claim.key.value) {
                        throw CancellationException("语音资源保存权已失效")
                    }
                    val attached = try {
                        attachLocked(audio)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        requireNotCancelled(
                            audio.receipt.audioId,
                            dao.resource(audio.receipt.audioId)
                        )
                        // 文件及 sidecar 已持久化，保留回执供当前进程或重开后免费补挂接。
                        Result.failure<Unit>(throwable)
                            .logFailure("CompanionVoiceStore") { "audio attachment pending" }
                        audio.copy(receipt = audio.receipt.copy(attachmentState = CompanionAudioAttachmentState.PENDING))
                    }
                    checkCurrent(isCurrent)
                    attached
                } catch (cancellation: CancellationException) {
                    // 在释放锁之前撤销 READY，避免另一个 lookup 看见已取消的成功缓存。
                    cancellationHandled = true
                    preserveCancellationFailure(cancellation) { cancelPublicationLocked(claim) }
                    throw cancellation
                }
            }
        } catch (cancellation: CancellationException) {
            if (!cancellationHandled) {
                preserveCancellationFailure(cancellation) { cancelPublication(claim) }
            }
            throw cancellation
        }
    }

    override suspend fun retryAttachment(audio: CompanionVoiceAudio): CompanionVoiceAudio =
        mutex.withLock {
            validateClip(audio.clip)
            attachLocked(audio)
        }

    override suspend fun recoverPending(): List<CompanionVoiceAudio> =
        mutex.withLock { recoverLocked() }

    override suspend fun releaseOwner(ownerToken: String) = mutex.withLock {
        pins.remove(ownerToken)
        database.withTransaction {
            dao.resources().filter { it.claimToken == ownerToken }.forEach { row ->
                dao.upsertResource(row.copy(claimToken = null, leaseUntil = null))
            }
        }
    }

    override suspend fun isSourceCurrent(source: CompanionReplySource): Boolean =
        database.withTransaction {
            if (source.attemptId == null || source.parserVersion != CompanionReplyParser.PARSER_VERSION) return@withTransaction false
            val message = database.conversationDao().getMessage(source.messageId)
                ?: return@withTransaction false
            if (message.roleplayInteractionMode != "offline_dialogue" || message.roleplayOutputFormat == "LONGFORM") {
                return@withTransaction false
            }
            val metadata = sourceStorage.read(message).replyMetadata ?: return@withTransaction false
            metadata.messageId == source.messageId && metadata.conversationId == source.conversationId &&
                    metadata.turnId == source.turnId && metadata.sourceRevision == source.revision &&
                    metadata.rawHash == source.rawHash && metadata.speakerId == source.characterId &&
                    metadata.selectedAttemptId == source.attemptId && metadata.protocolVersion == source.protocolVersion &&
                    metadata.presentation?.sourceSettingsRevision == source.configurationRevision
        }

    override fun observeSourceChanges(): Flow<Unit> = dao.observeSourceChanges().map { Unit }

    override fun observeFavorites(characterId: String): Flow<List<CompanionVoiceFavorite>> =
        combine(dao.observeResourceChanges(), dao.observeSourceChanges()) { _, _ -> Unit }
            .map {
                mutex.withLock {
                    dao.favorites().filter { it.characterId == characterId }.mapNotNull { row ->
                        favoriteDomain(row)
                    }
                }
            }.flowOn(Dispatchers.IO)

    override fun observeUsage(): Flow<CompanionVoiceCacheUsage> = dao.observeResourceChanges()
        .map { usage() }.flowOn(Dispatchers.IO)

    override suspend fun favorite(audio: CompanionVoiceAudio): CompanionVoiceFavorite =
        mutex.withLock {
            validateClip(audio.clip)
            val attached = attachLocked(audio)
            val source = attached.clip.segment.unit.source
            val id = companionRawHash(
                listOf(
                    attached.receipt.audioId,
                    source.messageId,
                    source.revision.toString(),
                    attached.clip.segment.range.start.toString(),
                    attached.clip.segment.range.endExclusive.toString(),
                    attached.clip.spokenText
                ).joinToString("\u0000")
            )
            val row = database.withTransaction {
                requireNotCancelled(
                    attached.receipt.audioId,
                    dao.resource(attached.receipt.audioId)
                )
                val existing = dao.favorite(id)
                existing ?: CompanionVoiceFavoriteEntity(
                    id,
                    attached.receipt.audioId,
                    source.characterId,
                    source.messageId,
                    source.revision,
                    AppJson.gson.toJson(attached.clip),
                    clock(),
                ).also { dao.upsertFavorite(it) }
            }
            requireNotNull(favoriteDomain(row)) { "声音收藏保存失败" }
        }

    override suspend fun unfavorite(favoriteId: String) =
        mutex.withLock { dao.deleteFavorite(favoriteId) }

    override suspend fun favoriteAudio(
        favoriteId: String,
        ownerToken: String
    ): CompanionVoiceAudio? = mutex.withLock {
        val favorite = dao.favorite(favoriteId)?.let { favoriteDomain(it) } ?: return@withLock null
        if (!publisher.validate(favorite.audio.receipt)) return@withLock null
        pin(ownerToken, favorite.audio.receipt.audioId)
        dao.resource(favorite.audio.receipt.audioId)?.let { row ->
            dao.upsertResource(row.copy(lastUsedAt = clock()))
        }
        favorite.audio
    }

    override suspend fun usage(): CompanionVoiceCacheUsage = mutex.withLock { usageLocked() }

    override suspend fun trimCache(maxBytes: Long): CompanionVoiceCacheUsage = mutex.withLock {
        require(maxBytes >= 0) { "语音缓存上限无效" }
        recoverLocked()
        evictLocked(maxBytes, clearPending = false)
        cleanupOrphansLocked()
        usageLocked()
    }

    override suspend fun clearCache(): CompanionVoiceCacheUsage = mutex.withLock {
        recoverLocked()
        evictLocked(0, clearPending = true)
        cleanupOrphansLocked()
        usageLocked()
    }

    private suspend fun lookupLocked(key: SpeechRequestKey): CompanionAudioReceipt? {
        var row = dao.resourceForKey(key.value) ?: return null
        if (isCancelled(row.id, row)) return null
        if (row.state == "PENDING") {
            // 本实例正在发布的回执仍受 generation 管理，后台清理/查询不能提前补成 READY。
            if (pins.values.any { row.id in it }) return null
            val persisted = publisher.publishedReceipts()
                .firstOrNull { it.receipt.audioId == row.id && it.receipt.key == key }
            if (persisted == null) return null
            val attached = attachLocked(persisted)
            row = requireNotNull(dao.resource(attached.receipt.audioId))
        }
        val receipt = row.receipt()
        if (row.state != "READY" || !publisher.validate(receipt)) {
            if (row.state == "READY") dao.upsertResource(
                row.copy(
                    state = "PENDING",
                    claimToken = null,
                    leaseUntil = null
                )
            )
            return null
        }
        dao.upsertResource(row.copy(lastUsedAt = clock()))
        return receipt
    }

    private suspend fun attachLocked(audio: CompanionVoiceAudio): CompanionVoiceAudio {
        requireNotCancelled(audio.receipt.audioId, dao.resource(audio.receipt.audioId))
        require(publisher.validate(audio.receipt)) { "语音文件缺失或校验失败" }
        beforeAttachment(audio)
        val attached =
            audio.copy(receipt = audio.receipt.copy(attachmentState = CompanionAudioAttachmentState.ATTACHED))
        database.withTransaction {
            val current = dao.resource(audio.receipt.audioId)
            requireNotCancelled(audio.receipt.audioId, current)
            require(current == null || current.requestKey == audio.receipt.key.value) { "语音持久回执身份不匹配" }
            val existingKey = dao.resourceForKey(audio.receipt.key.value)
            val cacheKey =
                if (existingKey == null || existingKey.id == audio.receipt.audioId) audio.receipt.key.value else null
            dao.upsertResource(
                CompanionVoiceResourceEntity(
                    audio.receipt.audioId,
                    audio.receipt.key.value,
                    cacheKey,
                    "READY",
                    null,
                    null,
                    audio.receipt.path,
                    audio.receipt.mimeType,
                    audio.receipt.sha256,
                    audio.receipt.sizeBytes,
                    current?.clipJson?.takeIf { it.isNotBlank() }
                        ?: AppJson.gson.toJson(audio.clip),
                    audio.receipt.createdAt,
                    clock(),
                )
            )
        }
        // marker 写失败也保留实际已提交的 Room 回执，下次重开会幂等修复 marker。
        try {
            publisher.markAttached(attached)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Result.failure<Unit>(throwable)
                .logFailure("CompanionVoiceStore") { "audio receipt marker pending" }
        }
        return attached
    }

    private suspend fun recoverLocked(): List<CompanionVoiceAudio> {
        val recovered = mutableListOf<CompanionVoiceAudio>()
        publisher.publishedReceipts().forEach { audio ->
            currentCoroutineContext().ensureActive()
            val row = dao.resource(audio.receipt.audioId)
            if (isCancelled(audio.receipt.audioId, row)) return@forEach
            if (row?.state == "PENDING" && pins.values.any { row.id in it }) return@forEach
            if (row?.state == "READY" && audio.receipt.attachmentState == CompanionAudioAttachmentState.ATTACHED) return@forEach
            try {
                validateClip(audio.clip)
                recovered += attachLocked(audio)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure<Unit>(throwable)
                    .logFailure("CompanionVoiceStore") { "audio recovery remains pending" }
            }
        }
        return recovered
    }

    private suspend fun cancelPublication(claim: CompanionAudioClaim) =
        mutex.withLock { cancelPublicationLocked(claim) }

    private suspend fun cancelPublicationLocked(claim: CompanionAudioClaim) {
        cancelledAudioIds += claim.audioId
        pins[claim.ownerToken]?.remove(claim.audioId)
        var mayDelete = false
        var recordFailure: Throwable? = null
        try {
            val matches = database.withTransaction {
                val row = dao.resource(claim.audioId)
                if (row != null && row.requestKey != claim.key.value) return@withTransaction false
                mayDelete = dao.favoriteReferences(claim.audioId) == 0 &&
                        pins.values.none { claim.audioId in it }
                // 先持久化 tombstone；文件删除失败也不能由 sidecar/旧回执重新发布 READY。
                val tombstone =
                    row?.copy(state = CANCELLED_STATE, claimToken = null, leaseUntil = null)
                        ?: CompanionVoiceResourceEntity(
                            claim.audioId, claim.key.value,
                            claim.key.value.takeIf { dao.resourceForKey(it) == null },
                            CANCELLED_STATE, null, null, "", "", "", 0, "", clock(), clock(),
                        )
                dao.upsertResource(tombstone)
                true
            }
            if (!matches) {
                cancelledAudioIds.remove(claim.audioId)
                return
            }
            cancelledAudioIds.remove(claim.audioId)
        } catch (throwable: Throwable) {
            recordFailure = throwable
        }
        var cleanupFailure: Throwable? = null
        if (mayDelete) {
            try {
                publisher.delete(claim.audioId)
            } catch (throwable: Throwable) {
                cleanupFailure = throwable
            }
        }
        if (recordFailure != null || cleanupFailure != null) {
            val message = if (recordFailure != null) {
                "语音取消记录未能持久化；若文件清理也失败，不能保证重启后保留取消状态"
            } else "语音已标记取消，文件清理待重试"
            throw IOException(message, recordFailure ?: cleanupFailure).also { failure ->
                if (recordFailure != null && cleanupFailure != null) failure.addSuppressed(
                    cleanupFailure
                )
            }
        }
    }

    private suspend fun preserveCancellationFailure(
        cancellation: CancellationException,
        cleanup: suspend () -> Unit,
    ) = withContext(NonCancellable) {
        try {
            cleanup()
        } catch (throwable: Throwable) {
            cancellation.addSuppressed(throwable)
            Result.failure<Unit>(throwable)
                .logFailure("CompanionVoiceStore") { "cancelled audio persistence or cleanup incomplete" }
        }
    }

    private fun isCancelled(audioId: String, row: CompanionVoiceResourceEntity?): Boolean =
        audioId in cancelledAudioIds || row?.state == CANCELLED_STATE

    private fun requireNotCancelled(audioId: String, row: CompanionVoiceResourceEntity?) {
        require(!isCancelled(audioId, row)) { "这份音频请求已取消，请显式重新生成" }
    }

    private suspend fun favoriteDomain(row: CompanionVoiceFavoriteEntity): CompanionVoiceFavorite? {
        val resource = dao.resource(row.audioId) ?: return null
        val clip = runCatching {
            AppJson.gson.fromJson(row.clipJson, CompanionVoiceClip::class.java).also(::validateClip)
        }
            .logFailure("CompanionVoiceStore") { "favorite snapshot decode failed" }.getOrNull()
            ?: return null
        val sourceState =
            if (isSourceCurrent(clip.segment.unit.source)) CompanionVoiceSourceState.AVAILABLE
            else CompanionVoiceSourceState.UNAVAILABLE
        return CompanionVoiceFavorite(
            row.id,
            // 已存收藏是独立回执；缓存取消不撤销收藏，也不把资源重新标成 READY。
            CompanionVoiceAudio(
                resource.receipt().copy(attachmentState = CompanionAudioAttachmentState.ATTACHED),
                clip
            ),
            sourceState,
            row.createdAt
        )
    }

    private suspend fun usageLocked(): CompanionVoiceCacheUsage {
        val resources = dao.resources()
        val favorites = dao.favorites()
        val favoriteIds = favorites.map { it.audioId }.toSet()
        val ready = resources.filter { it.state == "READY" && it.id !in cancelledAudioIds }
        return CompanionVoiceCacheUsage(
            cacheBytes = ready.filterNot { it.id in favoriteIds }.sumOf { it.sizeBytes },
            cacheCount = ready.count { it.id !in favoriteIds },
            favoriteBytes = resources.filter { it.id in favoriteIds }.sumOf { it.sizeBytes },
            favoriteCount = favorites.size,
            pendingCount = resources.count { it.state == "PENDING" || isCancelled(it.id, it) },
        )
    }

    private suspend fun evictLocked(maxBytes: Long, clearPending: Boolean) {
        val favorites = dao.favorites().map { it.audioId }.toSet()
        val candidates = dao.resources().filterNot { it.id in favorites }
        var bytes = candidates.filter { it.state == "READY" }.sumOf { it.sizeBytes }
        for (row in candidates) {
            if (pins.values.any { row.id in it } || (row.claimToken != null && (row.leaseUntil
                    ?: 0) > clock())) continue
            val shouldDelete =
                (row.state == "READY" && bytes > maxBytes) || isCancelled(row.id, row) ||
                        (row.state == "PENDING" && clearPending)
            if (!shouldDelete) continue
            if (isCancelled(row.id, row) && row.state != CANCELLED_STATE) {
                dao.upsertResource(
                    row.copy(
                        state = CANCELLED_STATE,
                        claimToken = null,
                        leaseUntil = null
                    )
                )
                cancelledAudioIds.remove(row.id)
            }
            // 先保留 Room 引用，文件删除失败不会把仍存在的文件变成无名孤儿。
            publisher.delete(row.id)
            database.withTransaction {
                if (dao.favoriteReferences(row.id) == 0) dao.deleteResource(row.id)
            }
            if (row.state == "READY") bytes -= row.sizeBytes
        }
    }

    private suspend fun cleanupOrphansLocked() {
        val retained = dao.resources().filter {
            it.state == "READY" ||
                    (it.claimToken != null && (it.leaseUntil ?: 0) > clock())
        }.map { it.id }.toMutableSet()
        retained += dao.favorites().map { it.audioId }
        retained += publisher.publishedReceipts().map { it.receipt.audioId }
        pins.values.forEach { retained += it }
        publisher.cleanupOrphans(retained)
    }

    private fun CompanionVoiceResourceEntity.receipt(): CompanionAudioReceipt =
        CompanionAudioReceipt(
            id, SpeechRequestKey(requestKey), path, mimeType, sha256, sizeBytes, createdAt,
            if (state == "READY") CompanionAudioAttachmentState.ATTACHED else CompanionAudioAttachmentState.PENDING,
        )

    private fun pin(ownerToken: String, audioId: String) {
        require(ownerToken.isNotBlank()) { "语音任务所有者无效" }
        pins.getOrPut(ownerToken) { mutableSetOf() }.add(audioId)
    }

    private suspend fun checkCurrent(isCurrent: suspend () -> Boolean) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("旧语音任务已取消")
        currentCoroutineContext().ensureActive()
    }

    private fun requireKey(key: SpeechRequestKey) {
        require(Regex("[a-f0-9]{64}").matches(key.value)) { "语音缓存标识无效" }
    }

    private fun validateClip(clip: CompanionVoiceClip) {
        val segment = clip.segment
        val range = segment.range
        require(
            range.unitId == segment.unit.id && range.start >= 0 && range.endExclusive <= segment.unit.text.length &&
                    range.start < range.endExclusive && segment.unit.text.substring(
                range.start,
                range.endExclusive
            ) == segment.originalText &&
                    clip.spokenText.isNotBlank() && clip.languageTag.isNotBlank()
        ) { "语音选区快照无效" }
        val endpoint = URI(clip.synthesis.endpoint)
        require(endpoint.rawUserInfo == null && endpoint.rawQuery == null && endpoint.rawFragment == null) {
            "语音端点快照不能包含认证信息"
        }
        require(clip.synthesis.parameters.keys.none { name ->
            val normalized = name.filter(Char::isLetterOrDigit).lowercase()
            listOf(
                "apikey",
                "authorization",
                "token",
                "secret",
                "password",
                "base64",
                "path"
            ).any { it in normalized }
        } &&
                clip.synthesis.parameters.values.none {
                    it.startsWith(
                        "data:",
                        true
                    )
                }) { "语音参数快照包含敏感数据" }
    }

    companion object {
        private const val CANCELLED_STATE = "CANCELLED"
        private const val CLAIM_LEASE_MILLIS = 5 * 60 * 1000L
    }
}
