package com.example.myapplication.data.repository.roleplay

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.repository.LocalImageStore
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionResourceResolver
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 仅清理本实例刚导入且尚未发布的文件。已保存素材的移除只是解除引用。 */
class AndroidCompanionResourcesRepository(
    private val context: Context,
    private val imageStore: LocalImageStore,
    private val settingsStore: SettingsStore,
    private val references: suspend () -> Set<String>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : CompanionResourcesRepository {
    private val importMutex = Mutex()
    private val unpublished = mutableSetOf<String>()

    override suspend fun importImage(
        uri: String,
        kind: CompanionImageKind,
        name: String
    ): CompanionImageResource {
        require(name.trim().length in 1..CompanionVisualValidation.MAX_NAME) { "请填写图片名称" }
        val path = imageStore.copyToAppStorage(uri.toUri(), "companion_stage")
            ?: error("图片导入失败，请重新选择")
        try {
            val size = withContext(dispatcher) { imageSize(path) }
                ?: error("所选文件不是可读取的图片，或图片过大")
            importMutex.withLock { unpublished += path }
            return CompanionImageResource(newId(), name.trim(), path, kind, size.first, size.second)
        } catch (cancelled: CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable) { imageStore.deleteIfLocalAsync(path) }
            throw cancelled
        } catch (failure: Exception) {
            imageStore.deleteIfLocalAsync(path)
            throw failure
        }
    }

    override suspend fun resolve(
        visuals: CompanionVisualSettings,
        scenario: RoleplayScenario,
        assistant: Assistant?
    ): CompanionResolvedResources =
        withContext(dispatcher) {
            val validity = mutableMapOf<Pair<String, String>, Boolean>()
            CompanionResourceResolver.resolve(visuals, scenario, assistant) { image ->
                validity.getOrPut(image.uri to image.url) {
                    if (image.uri.isNotBlank()) readable(image.uri) else image.url.startsWith("https://")
                }
            }
        }

    override suspend fun save(
        assistantId: String, expectedRevision: Long, visuals: CompanionVisualSettings,
        importedPaths: Set<String>
    ): CompanionSettingsWriteResult {
        require(CompanionVisualValidation.error(visuals) == null) {
            CompanionVisualValidation.error(
                visuals
            ).orEmpty()
        }
        val referenced = visuals.resources.map { it.path }.toSet()
        check(withContext(dispatcher) {
            importedPaths.filter { it in referenced }.all { imageSize(it) != null }
        }) {
            "新导入的图片已不可用，请重新导入；草稿仍保留"
        }
        val result = importMutex.withLock {
            val written = settingsStore.updateCompanionSettings(
                assistantId,
                expectedRevision,
                CompanionSettingsPatch(visuals = visuals)
            )
            if (written is CompanionSettingsWriteResult.Saved) unpublished.removeAll(referenced)
            written
        }
        if (result is CompanionSettingsWriteResult.Saved) discardImports(importedPaths - referenced)
        return result
    }

    override suspend fun discardImports(paths: Set<String>) {
        if (paths.isEmpty()) return
        importMutex.withLock {
            val candidates = paths.intersect(unpublished)
            if (candidates.isEmpty()) return@withLock
            val liveReferences = withContext(dispatcher) { references() }
            candidates.forEach { path ->
                if (path !in liveReferences && Uri.fromFile(File(path))
                        .toString() !in liveReferences
                ) {
                    val deleted = imageStore.deleteIfLocalAsync(path)
                    check(deleted || !File(path).exists()) { "临时图片暂无法清理，请重试；已保存素材仍保留" }
                }
                unpublished.remove(path)
            }
        }
    }

    private fun readable(uri: String): Boolean = when {
        uri.startsWith("https://") -> true
        uri.startsWith("content://") -> runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri.toUri())
                ?.use { BitmapFactory.decodeStream(it, null, options) }
            options.outWidth > 0 && options.outHeight > 0
        }.getOrDefault(false)

        else -> com.example.myapplication.data.repository.normalizeImagePath(uri)
            ?.let(::imageSize) != null
    }

    private fun imageSize(path: String): Pair<Int, Int>? {
        val file = File(path)
        if (!file.isFile || file.length() !in 1..MAX_IMAGE_BYTES) return null
        // 解码前关闭文件流，避免 native decoder 保留待清理图片的路径/文件句柄。
        val bytes = file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > MAX_IMAGE_BYTES) return null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..32_768 || bounds.outHeight !in 1..32_768 ||
            bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L
        ) return null
        val sample = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }.first {
                maxOf(
                    bounds.outWidth,
                    bounds.outHeight
                ) / it <= 128
            }
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sample) ?: return null
        decoded.recycle()
        return bounds.outWidth to bounds.outHeight
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
    }
}

/** 完整保存图的引用保护。消息图片和旧场景/头像也算引用，不只扫当前角色。 */
internal class CompanionImageReferences(
    private val database: ChatDatabase,
    private val settingsStore: SettingsStore
) {
    suspend fun deleteIfUnreferenced(path: String?, imageStore: LocalImageStore): Boolean =
        withContext(Dispatchers.IO) {
            val normalized = com.example.myapplication.data.repository.normalizeImagePath(path)
                ?: return@withContext false
            val canonical = runCatching { File(normalized).canonicalPath }.getOrNull()
                ?: return@withContext false
            val references = try {
                read()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@withContext false
            }
            if (canonical in references) false else imageStore.deleteIfLocalAsync(path)
        }

    suspend fun read(): Set<String> = buildSet {
        val originalCompanionReferences = settingsStore.companionImageReferences()
            ?: error("演出素材引用暂不可完整核对")

        fun collect(value: JsonElement) {
            when {
                value.isJsonPrimitive && value.asJsonPrimitive.isString -> {
                    val text = value.asString
                    add(text)
                    com.example.myapplication.data.repository.normalizeImagePath(text)
                        ?.let { local ->
                            runCatching { File(local).canonicalPath }.getOrNull()?.let { add(it) }
                        }
                }

                value.isJsonArray -> value.asJsonArray.forEach(::collect)
                value.isJsonObject -> value.asJsonObject.entrySet().forEach { collect(it.value) }
            }
        }
        collect(AppJson.gson.toJsonTree(settingsStore.settingsFlow.first()))
        originalCompanionReferences.forEach { collect(com.google.gson.JsonPrimitive(it)) }
        database.roleplayDao().listScenarios().forEach { collect(AppJson.gson.toJsonTree(it)) }
        database.conversationDao().listConversations().forEach { conversation ->
            database.conversationDao().listMessages(conversation.id).forEach { message ->
                collect(AppJson.gson.toJsonTree(message))
                listOf(message.partsJson, message.attachmentsJson).forEach { raw ->
                    runCatching { com.google.gson.JsonParser.parseString(raw) }.getOrNull()
                        ?.let(::collect)
                }
            }
        }
    }
}
