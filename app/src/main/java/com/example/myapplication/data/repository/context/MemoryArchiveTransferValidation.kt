package com.example.myapplication.data.repository.context

import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.model.*

/** 校验整个图后再恢复，不能用 mapNotNull/IGNORE 把坏边变成部分成功。 */
internal object MemoryArchiveTransferValidation {
    fun validate(data: MemoryArchiveTransferData) {
        val collections = listOf(
            data.spaces, data.records, data.recordRevisions, data.sources,
            data.recordSources, data.events, data.eventMembers, data.residents, data.states,
            data.knowledgeRevisions, data.derivedLinks, data.origins, data.vectors
        )
        require(collections.sumOf { it.size.toLong() } <= MemoryTransferJson.MAX_ITEMS) { "记忆档案条目过多" }
        val spaces = unique(data.spaces) { it.id }
        val records = unique(data.records) { it.id }
        val sources = unique(data.sources) { it.id }
        val events = unique(data.events) { it.id }
        val residents = unique(data.residents) { it.id }
        val states = unique(data.states) { it.id }
        val revisions = unique(data.recordRevisions) { it.recordId to it.revision }
        val recordSourcesByRevision =
            data.recordSources.groupBy { it.recordId to it.recordRevision }
        val membersByEvent = data.eventMembers.groupBy { it.eventId }
        val knowledge = unique(data.knowledgeRevisions) { Triple(it.kind, it.ownerId, it.revision) }
        unique(data.recordSources) {
            listOf(
                it.recordId,
                it.recordRevision,
                it.source.sourceId,
                it.source.start,
                it.source.end
            )
        }
        unique(data.eventMembers) { it.eventId to it.recordId }
        unique(data.derivedLinks) { Triple(it.kind, it.ownerId, it.record.recordId) }
        unique(data.states) { Triple(it.spaceId, it.personaKey, it.key) }

        fun owner(
            spaceId: String,
            characterId: String,
            personaKey: String
        ): Triple<String, String, String> {
            require(spaces[spaceId]?.characterId == characterId && personaKey.isNotBlank()) { "记忆档案归属不一致" }
            return Triple(spaceId, characterId, personaKey)
        }

        fun recordOwner(id: String) = requireNotNull(records[id]) { "记忆档案缺少关联记录" }.let {
            owner(it.spaceId, it.characterId, it.personaKey)
        }

        fun knowledgeOwner(kind: MemoryMaterialKind, id: String): Triple<String, String, String> =
            when (kind) {
                MemoryMaterialKind.RESIDENT -> requireNotNull(residents[id]) { "认知版本缺少所有者" }.let {
                    owner(
                        it.spaceId,
                        it.characterId,
                        it.personaKey
                    )
                }

                MemoryMaterialKind.STATE -> requireNotNull(states[id]) { "状态版本缺少所有者" }.let {
                    owner(
                        it.spaceId,
                        it.characterId,
                        it.personaKey
                    )
                }

                else -> throw IllegalArgumentException("认知所有者类型无效")
            }

        fun sourceRef(ref: MemorySourceRef, expectedOwner: Triple<String, String, String>) {
            val source = requireNotNull(sources[ref.sourceId]) { "记忆档案缺少来源" }
            require(
                owner(source.spaceId, source.characterId, source.personaKey) == expectedOwner &&
                        ref.sourceRevision == source.messageRevision && ref.start >= 0 && ref.end >= ref.start && ref.end <= source.contentLength
            ) {
                "记忆来源归属、版本或范围无效"
            }
            if (source.status == MemorySourceStatus.AVAILABLE) {
                require(
                    ref.end > ref.start && boundary(source.content, ref.start) && boundary(
                        source.content,
                        ref.end
                    )
                ) { "记忆来源范围无效" }
            }
        }

        fun recordRef(ref: MemoryRecordVersion, expectedOwner: Triple<String, String, String>) {
            val record = requireNotNull(records[ref.recordId]) { "记忆档案缺少支持记录" }
            require(
                recordOwner(record.id) == expectedOwner && ref.revision > 0 && ref.revision <= record.revision &&
                        (revisions.containsKey(record.id to ref.revision) || record.status == MemoryRecordStatus.DELETED)
            ) {
                "记忆支持记录的版本或归属无效"
            }
        }

        data.spaces.forEach {
            validId(it.id); validId(it.characterId)
            require(it.generation > 0 && it.createdAt >= 0 && it.updatedAt >= 0) { "记忆空间元数据无效" }
        }
        data.sources.forEach {
            validId(it.id); owner(it.spaceId, it.characterId, it.personaKey)
            require(
                it.messageRevision >= 0 && it.contentLength >= 0 && it.contentLength <= MemoryTransferJson.MAX_BYTES &&
                        it.contentFormatVersion in 1..2 && it.occurredAt >= 0 && it.ingestedAt >= 0 &&
                        it.visibleCharacterIds.distinct().size == it.visibleCharacterIds.size
            ) { "记忆来源元数据无效" }
            if (it.status == MemorySourceStatus.AVAILABLE) {
                require(
                    it.kind != MemorySourceKind.LEGACY_HINT && it.messageRevision > 0 && it.content.isNotBlank() &&
                            it.content.length == it.contentLength && it.characterId in it.visibleCharacterIds &&
                            MemoryArchiveIdentity.fingerprint(it.content) == it.contentFingerprint
                ) { "可用来源快照校验失败" }
            }
            require(it.status != MemorySourceStatus.DELETED || it.content.isEmpty()) { "已删除来源不能携带正文" }
        }
        data.records.forEach {
            validId(it.id); owner(it.spaceId, it.characterId, it.personaKey)
            require(
                it.revision in 1 until Long.MAX_VALUE && it.importance in 0..100 &&
                        it.createdAt >= 0 && it.updatedAt >= 0 && it.lastUsedAt >= 0 &&
                        it.datePrecision in setOf(
                    "unknown",
                    "day",
                    "month",
                    "year",
                    "exact",
                    "relative",
                    "fictional"
                )
            ) { "记忆条目元数据无效" }
            val current = revisions[it.id to it.revision]
            require(
                if (it.status == MemoryRecordStatus.DELETED) it.content.isEmpty() && current?.content.orEmpty()
                    .isEmpty()
                else current != null && current.content == it.content
            ) { "记忆当前正文与版本不一致" }
            if (it.status != MemoryRecordStatus.DELETED && it.content.isNotEmpty()) {
                require(
                    recordSourcesByRevision[it.id to it.revision].orEmpty().isNotEmpty()
                ) { "记忆缺少当前来源" }
            }
        }
        data.recordRevisions.forEach {
            val record = requireNotNull(records[it.recordId]) { "正文版本缺少记录" }
            require(
                it.revision in 1..record.revision && it.createdAt >= 0 &&
                        (record.status != MemoryRecordStatus.DELETED || it.content.isEmpty())
            ) { "正文版本无效" }
            if (it.content.isNotEmpty()) require(
                recordSourcesByRevision[it.recordId to it.revision].orEmpty()
                    .none { link -> sources[link.source.sourceId]?.status == MemorySourceStatus.DELETED }) { "已删除来源不能从正文缓存恢复" }
        }
        data.recordSources.forEach {
            require(revisions.containsKey(it.recordId to it.recordRevision)) { "来源引用缺少正文版本" }
            sourceRef(it.source, recordOwner(it.recordId))
        }
        data.events.forEach {
            validId(it.id); owner(it.spaceId, it.characterId, it.personaKey)
            require(it.summaryRevision >= 0 && it.generation > 0 && it.createdAt >= 0 && it.updatedAt >= 0) { "事件元数据无效" }
            require(it.status != MemoryEventStatus.DELETED || it.title.isEmpty() && it.summary.isEmpty() && it.currentState.isEmpty()) { "已删除事件不能携带正文" }
            if (it.title.isNotEmpty() || it.summary.isNotEmpty() || it.currentState.isNotEmpty()) {
                require(membersByEvent[it.id].orEmpty().none { member ->
                    val record = records[member.recordId]
                    record?.status == MemoryRecordStatus.DELETED ||
                            listOfNotNull(record?.revision, member.coveredRevision).distinct()
                                .any { revision ->
                                    recordSourcesByRevision[member.recordId to revision].orEmpty()
                                        .any { link ->
                                            sources[link.source.sourceId]?.status == MemorySourceStatus.DELETED
                                        }
                                }
                }) { "已删除来源不能从事件缓存恢复正文" }
            }
        }
        val eventForRecord = mutableMapOf<String, String>()
        data.eventMembers.forEach {
            val event = requireNotNull(events[it.eventId]) { "事件成员缺少事件" }
            require(
                recordOwner(it.recordId) == owner(
                    event.spaceId,
                    event.characterId,
                    event.personaKey
                )
            ) { "事件成员跨越归属" }
            require(
                eventForRecord.put(it.recordId, it.eventId)
                    .let { old -> old == null || old == it.eventId }) { "记忆同时属于多个事件" }
            it.coveredRevision?.let { revision ->
                recordRef(
                    MemoryRecordVersion(
                        it.recordId,
                        revision
                    ), recordOwner(it.recordId)
                )
            }
            require(it.addedAt >= 0) { "事件成员时间无效" }
        }
        data.residents.forEach {
            validId(it.id); owner(it.spaceId, it.characterId, it.personaKey)
            require(it.revision in 1 until Long.MAX_VALUE && it.status in RESIDENT_STATUSES && it.createdAt >= 0 && it.updatedAt >= 0) { "长期认知元数据无效" }
            require(it.status != "DELETED" || it.content.isEmpty()) { "已删除认知不能携带正文" }
            it.supersedesId?.let { id ->
                val previous = requireNotNull(residents[id]) { "认知替代关系缺少目标" }
                require(
                    id != it.id && previous.category == it.category && knowledgeOwner(
                        MemoryMaterialKind.RESIDENT,
                        id
                    ) == knowledgeOwner(MemoryMaterialKind.RESIDENT, it.id)
                ) { "认知替代关系无效" }
            }
            val seen = mutableSetOf(it.id)
            var next = it.supersedesId
            while (next != null) {
                require(seen.add(next)) { "认知替代关系存在循环" }
                next = residents[next]?.supersedesId
            }
        }
        data.states.forEach {
            validId(it.id); owner(it.spaceId, it.characterId, it.personaKey)
            require(
                it.revision in 1 until Long.MAX_VALUE && it.status in STATE_STATUSES && it.observedAt >= 0 &&
                        it.createdAt >= 0 && it.updatedAt >= 0 && (it.expiresAt == null || it.expiresAt > 0) && (it.endedAt == null || it.endedAt >= 0)
            ) { "当前状态元数据无效" }
            require(it.status != "DELETED" || it.content.isEmpty()) { "已删除状态不能携带正文" }
        }
        data.knowledgeRevisions.forEach {
            val expectedOwner = knowledgeOwner(it.kind, it.ownerId)
            val headRevision =
                if (it.kind == MemoryMaterialKind.RESIDENT) residents.getValue(it.ownerId).revision else states.getValue(
                    it.ownerId
                ).revision
            require(it.revision in 1..headRevision && it.status in RESIDENT_STATUSES + STATE_STATUSES && it.createdAt >= 0) { "认知历史版本无效" }
            unique(it.sourceRefs) { source -> source }
            unique(it.supportingRecords) { record -> record.recordId }
            it.sourceRefs.forEach { source -> sourceRef(source, expectedOwner) }
            it.supportingRecords.forEach { record -> recordRef(record, expectedOwner) }
            val deleted =
                if (it.kind == MemoryMaterialKind.RESIDENT) residents.getValue(it.ownerId).status == "DELETED" else states.getValue(
                    it.ownerId
                ).status == "DELETED"
            require(!deleted || it.content.isEmpty()) { "已删除认知不能携带历史正文" }
            if (it.content.isNotEmpty()) require(it.sourceRefs.none { ref -> sources[ref.sourceId]?.status == MemorySourceStatus.DELETED } &&
                    it.supportingRecords.none { ref ->
                        records[ref.recordId]?.status == MemoryRecordStatus.DELETED ||
                                recordSourcesByRevision[ref.recordId to ref.revision].orEmpty()
                                    .any { link -> sources[link.source.sourceId]?.status == MemorySourceStatus.DELETED }
                    }) { "已删除来源不能从认知缓存恢复" }
        }
        data.derivedLinks.forEach { recordRef(it.record, knowledgeOwner(it.kind, it.ownerId)) }
        fun checkHead(
            kind: MemoryMaterialKind,
            id: String,
            revision: Long,
            content: String,
            status: String
        ) {
            val head = knowledge[Triple(kind, id, revision)]
            require(head == null || head.content == content) { "认知当前正文与版本不一致" }
            val all = knowledge.keys.filter { it.first == kind && it.second == id }
            require(all.isEmpty() || head != null) { "认知历史缺少当前版本" }
            if (status != "DELETED" && content.isNotEmpty()) {
                require(head?.sourceRefs?.isNotEmpty() == true || data.derivedLinks.any { it.kind == kind && it.ownerId == id }) { "认知缺少直接来源或支持记录" }
            }
        }
        data.residents.forEach {
            checkHead(
                MemoryMaterialKind.RESIDENT,
                it.id,
                it.revision,
                it.content,
                it.status
            )
        }
        data.states.forEach {
            checkHead(
                MemoryMaterialKind.STATE,
                it.id,
                it.revision,
                it.content,
                it.status
            )
        }
        data.origins.forEach {
            val exists = when (it.kind) {
                MemoryTransferObjectKind.SPACE -> it.localId in spaces
                MemoryTransferObjectKind.RECORD -> it.localId in records
                MemoryTransferObjectKind.SOURCE -> it.localId in sources
                MemoryTransferObjectKind.EVENT -> it.localId in events
                MemoryTransferObjectKind.RESIDENT -> it.localId in residents
                MemoryTransferObjectKind.STATE -> it.localId in states
            }
            require(exists && it.localRevision >= 0 && it.origin.bundleVersion in 1..3 && it.origin.exportedAt >= 0 && it.origin.importedAt >= 0) { "导入出处引用无效" }
            val maxRevision = when (it.kind) {
                MemoryTransferObjectKind.SPACE -> 0L
                MemoryTransferObjectKind.SOURCE -> sources.getValue(it.localId).messageRevision
                MemoryTransferObjectKind.RECORD -> records.getValue(it.localId).revision
                MemoryTransferObjectKind.EVENT -> events.getValue(it.localId).summaryRevision
                MemoryTransferObjectKind.RESIDENT -> residents.getValue(it.localId).revision
                MemoryTransferObjectKind.STATE -> states.getValue(it.localId).revision
            }
            require(
                it.localRevision <= maxRevision && (it.kind !in setOf(
                    MemoryTransferObjectKind.RECORD,
                    MemoryTransferObjectKind.RESIDENT,
                    MemoryTransferObjectKind.STATE
                ) || it.localRevision > 0L)
            ) { "导入出处版本无效" }
            if (it.kind == MemoryTransferObjectKind.SOURCE) {
                val source = sources.getValue(it.localId)
                require(it.localRevision == source.messageRevision) { "来源出处版本不一致" }
                require(
                    source.status != MemorySourceStatus.AVAILABLE ||
                            (it.origin.originalSourceStatus == null || it.origin.originalSourceStatus == MemorySourceStatus.AVAILABLE) &&
                            it.origin.originalSourceKind != MemorySourceKind.LEGACY_HINT
                ) { "不可用的原始来源不能升级为可用快照" }
                require(it.origin.originalSourceStatus != MemorySourceStatus.DELETED || source.content.isEmpty()) { "已删除原始来源不能携带正文" }
            }
        }
        // 向量只是可重建缓存；细项不兼容时由导入器保留正文并报告需重建。
        data.vectors.forEach {
            require(it.spaceId in spaces && it.personaKey.isNotBlank()) { "向量清单归属无效" }
        }
    }

    fun validateLegacy(bundle: ContextDataBundle) {
        unique(bundle.memoryEntries) { it.id }
        unique(bundle.conversationSummaries) {
            listOf(
                it.conversationId,
                it.assistantId,
                it.memoryContext
            )
        }
        unique(bundle.conversationSummarySegments) { it.id }
        require(bundle.memoryEntries.size + bundle.conversationSummaries.size + bundle.conversationSummarySegments.size <= MemoryTransferJson.MAX_ITEMS) { "旧记忆资料条目过多" }
        bundle.memoryEntries.forEach {
            validId(it.id)
            require(it.importance in 0..100 && it.createdAt >= 0 && it.updatedAt >= 0 && it.lastUsedAt >= 0) { "旧记忆元数据无效" }
        }
        bundle.conversationSummaries.forEach { require(it.conversationId.isNotBlank() && it.coveredMessageCount >= 0 && it.updatedAt >= 0) { "旧摘要元数据无效" } }
        bundle.conversationSummarySegments.forEach { require(it.id.isNotBlank() && it.conversationId.isNotBlank() && it.messageCount >= 0 && it.createdAt >= 0 && it.updatedAt >= 0) { "旧摘要分段元数据无效" } }
    }

    private fun validId(id: String) =
        require(id.isNotBlank() && id.length <= 2048) { "记忆档案编号无效" }

    private fun boundary(text: String, index: Int): Boolean = index in 0..text.length &&
            (index == 0 || index == text.length || !(text[index - 1].isHighSurrogate() && text[index].isLowSurrogate()))

    private fun <T, K> unique(items: List<T>, key: (T) -> K): Map<K, T> =
        items.associateBy(key).also {
            require(it.size == items.size) { "记忆档案编号或引用重复" }
        }

    private val RESIDENT_STATUSES = setOf("READY", "ARCHIVED", "STALE", "DELETED")
    private val STATE_STATUSES = setOf("ACTIVE", "ENDED", "EXPIRED", "STALE", "DELETED")
}
