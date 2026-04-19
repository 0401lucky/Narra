package com.example.myapplication.model

import androidx.compose.runtime.Immutable

/**
 * 日记条目内容字段的共用契约。
 *
 * 持久化实体 [RoleplayDiaryEntry] 与尚未落库的 [RoleplayDiaryDraft] 都实现本接口，
 * 让 AI 生成 → 写库 → 重新编辑 这条链路上的调用方能用同一套访问器读取内容字段。
 */
interface RoleplayDiaryCore {
    val title: String
    val content: String
    val mood: String
    val weather: String
    val tags: List<String>
    val dateLabel: String
}

@Immutable
data class RoleplayDiaryEntry(
    val id: String,
    val conversationId: String,
    val scenarioId: String,
    override val title: String,
    override val content: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    override val mood: String = "",
    override val weather: String = "",
    override val tags: List<String> = emptyList(),
    override val dateLabel: String = "",
) : RoleplayDiaryCore

@Immutable
data class RoleplayDiaryDraft(
    override val title: String,
    override val content: String,
    override val mood: String = "",
    override val weather: String = "",
    override val tags: List<String> = emptyList(),
    override val dateLabel: String = "",
) : RoleplayDiaryCore

/**
 * 任意 [RoleplayDiaryCore] → [RoleplayDiaryDraft]：用于 Entry 反向编辑、AI 重生成等场景。
 * 对已是 Draft 的实例走 fast-path 返回自身，避免拷贝。
 */
fun RoleplayDiaryCore.toDraft(): RoleplayDiaryDraft = when (this) {
    is RoleplayDiaryDraft -> this
    else -> RoleplayDiaryDraft(
        title = title,
        content = content,
        mood = mood,
        weather = weather,
        tags = tags,
        dateLabel = dateLabel,
    )
}

/**
 * [RoleplayDiaryDraft] → [RoleplayDiaryEntry]：由调用方补齐持久化所需字段。
 * 字段本身不做 trim，让仓库层统一负责清洗（避免重复语义）。
 */
fun RoleplayDiaryDraft.toEntry(
    id: String,
    conversationId: String,
    scenarioId: String,
    sortOrder: Int,
    createdAt: Long,
    updatedAt: Long = createdAt,
): RoleplayDiaryEntry = RoleplayDiaryEntry(
    id = id,
    conversationId = conversationId,
    scenarioId = scenarioId,
    title = title,
    content = content,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    mood = mood,
    weather = weather,
    tags = tags,
    dateLabel = dateLabel,
)
