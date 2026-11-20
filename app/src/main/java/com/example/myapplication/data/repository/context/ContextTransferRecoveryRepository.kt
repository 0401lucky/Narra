package com.example.myapplication.data.repository.context

import androidx.room.withTransaction
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.companion.ContextTransferOperationEntity
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

interface CompanionAppearanceTransferRepository {
    suspend fun exportPackage(characterId: String): String
    suspend fun prepareAppearanceImport(
        rawJson: String,
        characterId: String
    ): CompanionAppearanceImportPreview

    suspend fun resumeOperation(operationId: String): ContextTransferOperationSummary
    suspend fun listPendingOperations(kind: ContextTransferOperationKind): List<ContextTransferOperationSummary>
    suspend fun cancelPreview(operationId: String)
}

interface ContextTransferRecoveryRepository : CompanionAppearanceTransferRepository {
    suspend fun exportPrivateCompanionData(assistantIds: Set<String>): List<CompanionContextTransferEntry>
    suspend fun preparePrivateImport(
        bundle: ContextDataBundle,
        memoryPlan: MemoryImportPlan?,
        expectedSettings: AppSettings
    ): ContextTransferOperationSummary

    suspend fun preparePrivateImportWithAvatar(
        bundle: ContextDataBundle,
        memoryPlan: MemoryImportPlan?,
        expectedSettings: AppSettings,
        avatar: ContextTransferAvatarInput
    ): ContextTransferOperationSummary = error("当前资料恢复服务不支持头像文件计划")
}

/** 文件、Room 和 DataStore 各自提交；持久回执使重试只补尚未完成的阶段。 */
class RoomContextTransferRecoveryRepository(
    private val database: ChatDatabase,
    private val settingsStore: SettingsStore,
    private val worldBookRepository: WorldBookRepository,
    private val presetRepository: PresetRepository,
    private val memoryTransferRepository: MemoryArchiveTransferRepository? = null,
    private val files: CompanionTransferFileStore,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val phaseHook: suspend (String, String) -> Unit = { _, _ -> },
) : ContextTransferRecoveryRepository {
    private val dao = database.contextTransferOperationDao()
    private val mutex = Mutex()
    private val codec = CompanionAppearanceTransferCodec()

    override suspend fun exportPackage(characterId: String): String {
        val settings = settingsStore.settingsFlow.first()
        require(settings.resolvedAssistants().any { it.id == characterId }) { "角色已不存在" }
        val companion = settings.companionSettings(characterId)
        require(companion.schemaVersion == 1 && companion.visuals.schemaVersion == 1) { "此角色的装扮版本不受支持" }
        return codec.encode(exportAppearance(companion.visuals))
    }

    override suspend fun exportPrivateCompanionData(assistantIds: Set<String>): List<CompanionContextTransferEntry> {
        val settings = settingsStore.settingsFlow.first()
        var totalBytes = 0L
        return settings.companionCharacterSettings.filterKeys { it in assistantIds }
            .map { (id, value) ->
                require(value.schemaVersion == 1 && value.voice.schemaVersion == 1 && value.visuals.schemaVersion == 1) { "陪伴偏好版本不受支持，未导出不完整资料" }
                CompanionContextTransferEntry(
                    id,
                    1,
                    value.zoneId,
                    value.writing,
                    value.voice,
                    exportAppearance(value.visuals)
                ).also {
                    totalBytes += AppJson.gson.toJson(it).toByteArray(Charsets.UTF_8).size
                    require(totalBytes <= MemoryTransferJson.MAX_BYTES) { "陪伴偏好及装扮素材超过资料包大小上限" }
                }
            }.also { codec.privateEntries(AppJson.gson.toJsonTree(it)) }
    }

    override suspend fun prepareAppearanceImport(
        rawJson: String,
        characterId: String
    ): CompanionAppearanceImportPreview {
        val pack = codec.decode(rawJson)
        val settings = settingsStore.settingsFlow.first()
        val assistant = requireNotNull(
            settings.resolvedAssistants()
                .firstOrNull { it.id == characterId }) { "目标角色已不存在" }
        val current = settings.companionSettings(characterId)
        require(current.schemaVersion == 1 && current.visuals.schemaVersion == 1) { "目标演出设置版本不受支持" }
        val bundle = ContextDataBundle(
            version = 3,
            companions = listOf(CompanionContextTransferEntry(characterId, appearance = pack))
        )
        val operation = prepare(
            bundle,
            null,
            settings,
            ContextTransferOperationKind.PUBLIC_APPEARANCE,
            "${assistant.name.ifBlank { "角色" }}的装扮"
        )
        return CompanionAppearanceImportPreview(
            operation.operationId,
            assistant.name,
            pack.visuals.outfits.size,
            pack.images.size
        )
    }

    override suspend fun preparePrivateImport(
        bundle: ContextDataBundle,
        memoryPlan: MemoryImportPlan?,
        expectedSettings: AppSettings
    ): ContextTransferOperationSummary =
        prepare(
            bundle.normalizedForContextImport(),
            memoryPlan,
            expectedSettings,
            ContextTransferOperationKind.PRIVATE_LIBRARY,
            "私人资料导入"
        )

    override suspend fun preparePrivateImportWithAvatar(
        bundle: ContextDataBundle,
        memoryPlan: MemoryImportPlan?,
        expectedSettings: AppSettings,
        avatar: ContextTransferAvatarInput
    ): ContextTransferOperationSummary {
        require(bundle.assistants.size == 1 && bundle.assistants.single().id == avatar.assistantId) { "头像来源角色与资料不一致" }
        return prepare(
            bundle.normalizedForContextImport(),
            memoryPlan,
            expectedSettings,
            ContextTransferOperationKind.PRIVATE_LIBRARY,
            "私人资料导入",
            FrozenContextTransferAvatar(avatar.assistantId, files.prepareImage(avatar.bytes))
        )
    }

    private suspend fun prepare(
        bundle: ContextDataBundle, memoryPlan: MemoryImportPlan?, expectedSettings: AppSettings,
        kind: ContextTransferOperationKind, title: String,
        avatar: FrozenContextTransferAvatar? = null,
    ): ContextTransferOperationSummary {
        require(bundle.version in 1..3) { "不支持的资料版本" }
        codec.privateEntries(bundle.companions?.let(AppJson.gson::toJsonTree))
        require(
            bundle.assistants.map { it.id }.distinct().size == bundle.assistants.size &&
                bundle.worldBookEntries.map { it.id }
                    .distinct().size == bundle.worldBookEntries.size &&
                bundle.presets.map { it.id }.distinct().size == bundle.presets.size
        ) { "导入资料标识重复，未创建计划" }
        require(!hasMemory(bundle) || memoryPlan != null) { "请先选择每个记忆空间的目标" }
        val builtinIds = BUILTIN_ASSISTANTS.map { it.id }.toSet() + HIDDEN_BUILTIN_ASSISTANT_IDS
        val imported =
            bundle.assistants.filter { it.id !in builtinIds && !it.isBuiltin }.associateBy { it.id }
        val companions = bundle.companions.orEmpty().associateBy { it.assistantId }
        val currentAssistants = expectedSettings.resolvedAssistants().associateBy { it.id }
        val targets = (imported.keys + companions.keys).sorted().map { id ->
            require(id in imported || id in currentAssistants) { "陪伴偏好缺少可恢复的本机角色" }
            val companion = expectedSettings.companionSettings(id)
            if (companions[id] != null) require(companion.schemaVersion == 1) { "目标陪伴设置版本不受支持" }
            FrozenContextTransferTarget(id, currentAssistants[id], imported[id], companion)
        }
        require(avatar == null || targets.any { it.assistantId == avatar.assistantId && it.importedAssistant != null }) { "头像导入目标不可写" }
        val roomPreconditions = database.withTransaction {
            bundle.worldBookEntries.associate { it.id to fingerprint(worldBookRepository.getEntry(it.id)) } to
                    bundle.presets.associate { it.id to fingerprint(presetRepository.getPreset(it.id)) }
        }
        val id = newId()
        require(dao.get(id) == null) { "本机导入计划标识已存在，未覆盖旧计划" }
        // 原资料只在 bundle 保存一份；targets 冻结本机 CAS，避免重复嵌入图片内容。
        val payload = FrozenContextTransferPlan(
            1,
            kind,
            title,
            bundle,
            memoryPlan,
            targets,
            roomPreconditions.first,
            roomPreconditions.second,
            avatar
        )
        // 先验证 ID 映射与目标数量，不能在 Room 已成功后才发现装扮不可挂接。
        val placeholderImages = allImages(payload).associate { image ->
            image.sha256 to CompanionPublishedTransferImage(
                image.sha256,
                "private-transfer:${image.sha256}",
                image.width,
                image.height
            )
        }
        targets.forEach {
            settingsTarget(
                id,
                it,
                companions[it.assistantId],
                placeholderImages,
                avatar
            )
        }
        val file = files.freezePlan(id, AppJson.gson.toJson(payload))
        require(file.created) { "本机冻结计划文件已存在，未复用未知旧计划" }
        val now = nowProvider()
        val entity = ContextTransferOperationEntity(
            id,
            1,
            kind.name,
            ContextTransferStage.PLANNED.name,
            0,
            file.sha256,
            targets.size,
            "[]",
            "[]",
            "",
            now,
            now
        )
        try {
            dao.insert(entity)
        } catch (failure: Exception) {
            // 没有业务写入；只清本次同 hash 的私有计划文件。
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                val existing = try {
                    dao.get(id)
                } catch (_: Exception) {
                    return@withContext
                }
                if (existing == null) runCatching { files.discardPlan(id, file.sha256) }
            }
            throw failure
        }
        return summary(entity, payload)
    }

    override suspend fun resumeOperation(operationId: String): ContextTransferOperationSummary =
        mutex.withLock {
            while (true) {
                val entity = requireNotNull(dao.get(operationId)) { "导入计划不存在" }
                if (stage(entity) == ContextTransferStage.CANCELLED) error("导入计划已取消")
                val payload = plan(entity)
                when (stage(entity)) {
                    ContextTransferStage.CANCELLED -> error("导入计划已取消")
                    ContextTransferStage.COMPLETE -> return@withLock summary(entity, payload)
                    ContextTransferStage.PLANNED -> {
                        database.withTransaction {
                            val current = requireNotNull(dao.get(operationId))
                            if (stage(current) != ContextTransferStage.PLANNED) return@withTransaction
                            require(current.planHash == entity.planHash) { "导入计划已改变" }
                            payload.bundle.worldBookEntries.forEach {
                                require(
                                    fingerprint(
                                        worldBookRepository.getEntry(it.id)
                                    ) == payload.expectedWorldBooks[it.id]
                                ) { "世界书在计划冻结后已改变，请重新预览" }
                            }
                            payload.bundle.presets.forEach {
                                require(
                                    fingerprint(
                                        presetRepository.getPreset(
                                            it.id
                                        )
                                    ) == payload.expectedPresets[it.id]
                                ) { "预设在计划冻结后已改变，请重新预览" }
                            }
                            val memoryResult = if (hasMemory(payload.bundle)) {
                                val repository =
                                    requireNotNull(memoryTransferRepository) { "记忆恢复服务不可用" }
                                when (val result = repository.importArchive(
                                    payload.bundle,
                                    requireNotNull(payload.memoryPlan)
                                )) {
                                    is MemoryArchiveImportResult.Imported -> result
                                    is MemoryArchiveImportResult.Conflict -> error(result.reason)
                                }
                            } else null
                            payload.bundle.worldBookEntries.forEach {
                                worldBookRepository.upsertEntry(
                                    it
                                )
                            }
                            payload.bundle.presets.forEach {
                                presetRepository.upsertPreset(
                                    it.normalized().copy(builtIn = false, userModified = true)
                                )
                            }
                            advance(
                                current.copy(
                                    memoryResultJson = memoryResult?.let(AppJson.gson::toJson)
                                        .orEmpty()
                                ), ContextTransferStage.ROOM_COMMITTED
                            )
                        }
                        phaseHook(operationId, "afterRoomCommit")
                    }

                    ContextTransferStage.ROOM_COMMITTED -> {
                        payload.memoryPlan?.let { memoryTransferRepository?.notifyImportCommitted(it) }
                        val images = files.publishImages(allImages(payload))
                        phaseHook(operationId, "afterFilesPublish")
                        database.withTransaction {
                            val current = requireNotNull(dao.get(operationId))
                            if (stage(current) == ContextTransferStage.ROOM_COMMITTED) advance(
                                current.copy(publishedImagesJson = AppJson.gson.toJson(images)),
                                ContextTransferStage.FILES_COMMITTED
                            )
                        }
                    }

                    ContextTransferStage.FILES_COMMITTED -> {
                        val completed = completedTargets(entity)
                        val published = publishedImages(entity).associateBy { it.sha256 }
                        require(published.keys == allImages(payload).map { it.sha256 }
                            .toSet()) { "素材发布回执不完整，未保存设置" }
                        files.validatePublishedImages(published.values.toList())
                        val next = payload.targets.firstOrNull { it.assistantId !in completed }
                        if (next == null) {
                            phaseHook(operationId, "beforeComplete")
                            database.withTransaction {
                                val current = requireNotNull(dao.get(operationId))
                                if (stage(current) == ContextTransferStage.FILES_COMMITTED && completedTargets(
                                        current
                                    ).size == payload.targets.size
                                ) advance(current, ContextTransferStage.COMPLETE)
                            }
                        } else {
                            val target = settingsTarget(
                                operationId,
                                next,
                                payload.bundle.companions.orEmpty()
                                    .firstOrNull { it.assistantId == next.assistantId },
                                published,
                                payload.avatar
                            )
                            when (settingsStore.applyContextTransferTarget(
                                operationId,
                                entity.planHash,
                                target
                            )) {
                                ContextTransferSettingsResult.Applied, ContextTransferSettingsResult.AlreadyApplied -> Unit
                                ContextTransferSettingsResult.Conflict -> error("目标角色或陪伴设置在计划冻结后已改变；已完成阶段保留，请重新预览剩余目标")
                                ContextTransferSettingsResult.UnsupportedVersion -> error("目标陪伴设置版本不受支持；已完成阶段保留")
                            }
                            phaseHook(operationId, "afterSettingsTarget:${next.assistantId}")
                            database.withTransaction {
                                val current = requireNotNull(dao.get(operationId))
                                if (stage(current) == ContextTransferStage.FILES_COMMITTED) {
                                    val retained = completedTargets(current) + next.assistantId
                                    advance(
                                        current.copy(
                                            completedSettingsJson = AppJson.gson.toJson(
                                                retained.sorted()
                                            )
                                        ), ContextTransferStage.FILES_COMMITTED
                                    )
                                }
                            }
                        }
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("导入阶段不可恢复")
        }

    override suspend fun listPendingOperations(kind: ContextTransferOperationKind): List<ContextTransferOperationSummary> =
        dao.pending(kind.name).map { entity ->
            val payload = try {
                plan(entity)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (payload == null) ContextTransferOperationSummary(
                entity.id,
                kind,
                stage(entity),
                "冻结导入计划暂不可读取",
                completedTargets(entity).size,
                entity.settingsTargetCount
            )
            else summary(entity, payload)
        }

    override suspend fun cancelPreview(operationId: String) {
        val cancelled = database.withTransaction {
            val entity = requireNotNull(dao.get(operationId)) { "导入计划不存在" }
            if (stage(entity) == ContextTransferStage.CANCELLED) entity else {
                require(stage(entity) == ContextTransferStage.PLANNED) { "已有阶段完成，不能把取消预览当作跨存储回滚" }
                advance(entity, ContextTransferStage.CANCELLED)
                requireNotNull(dao.get(operationId))
            }
        }
        files.discardPlan(cancelled.id, cancelled.planHash)
    }

    private suspend fun plan(entity: ContextTransferOperationEntity): FrozenContextTransferPlan {
        require(entity.planVersion == 1) { "不支持的本机导入计划版本" }
        val payload = AppJson.gson.fromJson(
            files.readPlan(entity.id, entity.planHash),
            FrozenContextTransferPlan::class.java
        )
        require(payload.version == 1 && payload.kind.name == entity.kind && payload.targets.size == entity.settingsTargetCount && payload.targets.map { it.assistantId }
            .distinct().size == payload.targets.size) { "冻结导入计划不完整" }
        require(completedTargets(entity).all { id -> payload.targets.any { it.assistantId == id } }) { "导入设置回执与目标不匹配" }
        return payload
    }

    private suspend fun advance(
        entity: ContextTransferOperationEntity,
        stage: ContextTransferStage
    ) {
        check(
            dao.advance(
                entity.id,
                entity.revision,
                entity.planHash,
                stage.name,
                entity.completedSettingsJson,
                entity.publishedImagesJson,
                entity.memoryResultJson,
                nowProvider()
            ) == 1
        ) { "导入阶段已被其它操作改变，请重试" }
    }

    private fun stage(entity: ContextTransferOperationEntity): ContextTransferStage =
        ContextTransferStage.entries.firstOrNull { it.name == entity.stage }
            ?: error("导入阶段版本不受支持")

    private fun completedTargets(entity: ContextTransferOperationEntity): Set<String> =
        AppJson.gson.fromJson(entity.completedSettingsJson, Array<String>::class.java).toSet()

    private fun publishedImages(entity: ContextTransferOperationEntity): List<CompanionPublishedTransferImage> =
        AppJson.gson.fromJson(
            entity.publishedImagesJson,
            Array<CompanionPublishedTransferImage>::class.java
        ).toList()

    private fun summary(
        entity: ContextTransferOperationEntity,
        payload: FrozenContextTransferPlan
    ) = ContextTransferOperationSummary(
        entity.id,
        payload.kind,
        stage(entity),
        payload.title,
        completedTargets(entity).size,
        payload.targets.size,
        entity.memoryResultJson.takeIf(String::isNotBlank)?.let {
            AppJson.gson.fromJson(
                it,
                MemoryArchiveImportResult.Imported::class.java
            ).vectorsNeedingRebuild
        } ?: 0,
        payload.targets.map { it.assistantId }.toSet()
    )

    private fun fingerprint(value: Any?): String? = value?.let {
        CompanionTransferImageValidation.sha256(
            AppJson.gson.toJson(it).toByteArray(Charsets.UTF_8)
        )
    }

    private fun hasMemory(bundle: ContextDataBundle): Boolean =
        bundle.memoryEntries.isNotEmpty() || bundle.conversationSummaries.isNotEmpty() || bundle.conversationSummarySegments.isNotEmpty() || bundle.memoryArchive?.isEmpty == false

    private fun allImages(payload: FrozenContextTransferPlan): List<CompanionTransferImage> =
        (payload.bundle.companions.orEmpty()
            .flatMap { it.appearance?.images.orEmpty() } + listOfNotNull(payload.avatar?.image)).distinctBy { it.sha256 }

    private suspend fun exportAppearance(visuals: CompanionVisualSettings): CompanionAppearancePackage {
        require(visuals.schemaVersion == 1 && CompanionVisualValidation.error(visuals) == null) { "演出装扮引用无效，未导出不完整素材" }
        val resourceIds =
            visuals.resources.mapIndexed { index, resource -> resource.id to "image_$index" }
                .toMap()
        val outfitIds =
            visuals.outfits.mapIndexed { index, outfit -> outfit.id to "outfit_$index" }.toMap()
        val images = linkedMapOf<String, CompanionTransferImage>()
        val readImages = mutableMapOf<String, CompanionTransferImage>()
        var totalImageBytes = 0L
        val resources = visuals.resources.mapIndexed { index, resource ->
            val image = readImages[resource.path] ?: files.readImage(resource).let { candidate ->
                CompanionTransferImageValidation.bytes(candidate)
                (images[candidate.sha256] ?: candidate).also { readImages[resource.path] = it }
            }
            if (image.sha256 !in images) {
                totalImageBytes += CompanionTransferImageValidation.bytes(image).size
                require(totalImageBytes <= 12L * 1024 * 1024) { "装扮素材超过 12 MB 上限" }
                images[image.sha256] = image
            }
            CompanionAppearanceResource(
                requireNotNull(resourceIds[resource.id]),
                publicLabel(resource.name, "图片 ${index + 1}"),
                image.sha256,
                resource.kind
            )
        }
        val data = CompanionAppearanceData(
            visuals.preferredMode,
            resources,
            visuals.outfits.mapIndexed { index, outfit ->
                CompanionOutfit(
                    requireNotNull(outfitIds[outfit.id]),
                    publicLabel(outfit.name, "套装 ${index + 1}"),
                    outfit.defaultPortraitId?.let(resourceIds::get),
                    outfit.expressions.mapValues { requireNotNull(resourceIds[it.value]) })
            },
            visuals.selectedOutfitId?.let(outfitIds::get),
            visuals.selectedBackgroundId?.let(resourceIds::get),
            visuals.decorationIds.map { requireNotNull(resourceIds[it]) },
            visuals.layout,
        )
        return CompanionAppearancePackage(1, data, images.values.toList()).also { codec.encode(it) }
    }

    private fun publicLabel(value: String, fallback: String): String = value.trim().takeIf {
        it.isNotEmpty() && it.length <= 60 && it.none(Char::isISOControl) && !Regex(
            "(?:[A-Za-z]:[\\\\/]|(?:file|content|https?)://|^/)",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(it)
    } ?: fallback

    private fun settingsTarget(
        operationId: String,
        target: FrozenContextTransferTarget,
        source: CompanionContextTransferEntry?,
        published: Map<String, CompanionPublishedTransferImage>,
        avatar: FrozenContextTransferAvatar? = null
    ): ContextTransferSettingsTarget {
        val appearance = source?.appearance?.let { pack ->
            val prefix = "transfer_" + operationId.replace(
                "-",
                "_"
            ) + "_" + CompanionTransferImageValidation.sha256(target.assistantId.toByteArray())
                .take(8)
            val resourceIds =
                pack.visuals.resources.mapIndexed { index, resource -> resource.id to "${prefix}_r$index" }
                    .toMap()
            val outfitIds =
                pack.visuals.outfits.mapIndexed { index, outfit -> outfit.id to "${prefix}_o$index" }
                    .toMap()
            val resources = pack.visuals.resources.map { resource ->
                val image =
                    requireNotNull(published[resource.contentHash]) { "装扮图片发布回执缺失" }
                CompanionImageResource(
                    requireNotNull(resourceIds[resource.id]),
                    resource.name,
                    image.path,
                    resource.kind,
                    image.width,
                    image.height
                )
            }
            val current = target.expectedCompanion.visuals
            require(current.schemaVersion == 1) { "目标演出配置版本不受支持" }
            current.copy(
                preferredMode = pack.visuals.preferredMode ?: current.preferredMode,
                resources = current.resources + resources,
                outfits = current.outfits + pack.visuals.outfits.map { outfit ->
                    CompanionOutfit(
                        requireNotNull(outfitIds[outfit.id]),
                        outfit.name,
                        outfit.defaultPortraitId?.let(resourceIds::get),
                        outfit.expressions.mapValues { requireNotNull(resourceIds[it.value]) })
                },
                selectedOutfitId = pack.visuals.selectedOutfitId?.let(outfitIds::get)
                    ?: current.selectedOutfitId,
                selectedBackgroundId = pack.visuals.selectedBackgroundId?.let(resourceIds::get)
                    ?: current.selectedBackgroundId,
                decorationIds = (current.decorationIds + pack.visuals.decorationIds.map {
                    requireNotNull(
                        resourceIds[it]
                    )
                }).distinct(),
                layout = pack.visuals.layout,
            )
                .also { require(CompanionVisualValidation.error(it) == null) { "合并装扮超过目标数量上限或引用冲突" } }
        }
        val patch = source?.let { entry ->
            CompanionSettingsPatch(
                entry.zoneId,
                entry.writing,
                appearance,
                entry.voice
            )
        }
            ?.takeIf { it.zoneId != null || it.writing != null || it.visuals != null || it.voice != null }
        val imported =
            if (avatar?.assistantId == target.assistantId) requireNotNull(target.importedAssistant).copy(
                avatarUri = requireNotNull(published[avatar.image.sha256]).path
            ) else target.importedAssistant
        return ContextTransferSettingsTarget(
            target.assistantId,
            target.expectedAssistant,
            imported,
            target.expectedCompanion.revision,
            patch
        )
    }
}

private data class FrozenContextTransferTarget(
    val assistantId: String,
    val expectedAssistant: Assistant?,
    val importedAssistant: Assistant?,
    val expectedCompanion: CompanionCharacterSettings,
)

private data class FrozenContextTransferPlan(
    val version: Int,
    val kind: ContextTransferOperationKind,
    val title: String,
    val bundle: ContextDataBundle,
    val memoryPlan: MemoryImportPlan?,
    val targets: List<FrozenContextTransferTarget>,
    val expectedWorldBooks: Map<String, String?>,
    val expectedPresets: Map<String, String?>,
    val avatar: FrozenContextTransferAvatar?,
)

private data class FrozenContextTransferAvatar(
    val assistantId: String,
    val image: CompanionTransferImage
)
