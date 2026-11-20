package com.example.myapplication.data.repository.roleplay

import com.example.myapplication.model.*
import kotlinx.coroutines.flow.Flow

interface CompanionReadingRepository {
    suspend fun authorize(scenarioId: String, characterId: String): CompanionReadingScope?
    suspend fun refreshScope(scope: CompanionReadingScope): CompanionReadingScope?
    suspend fun page(
        scope: CompanionReadingScope,
        before: CompanionReadingCursor? = null,
        limit: Int = 40
    ): CompanionReadingPage

    suspend fun readWindow(
        scope: CompanionReadingScope,
        oldestInclusive: CompanionReadingCursor
    ): List<CompanionReadingRecord>

    fun observeChanges(scope: CompanionReadingScope): Flow<Unit>

    suspend fun archiveChoices(scope: CompanionReadingScope): List<CompanionHistoryArchiveChoice> = emptyList()

    suspend fun authorizeArchive(
        scope: CompanionReadingScope,
        spaceId: String,
        runId: String,
    ): CompanionReadingScope? = null

    suspend fun readAll(scope: CompanionReadingScope): List<CompanionReadingRecord> {
        val records = mutableListOf<CompanionReadingRecord>()
        var cursor: CompanionReadingCursor? = null
        var hasOlder = true
        while (hasOlder) {
            val next = page(scope, cursor, 200)
            records += next.records
            check(!next.hasOlder || next.nextBefore != null && next.nextBefore != cursor) { "阅读分页游标不可用" }
            cursor = next.nextBefore
            hasOlder = next.hasOlder
        }
        return records.distinctBy { it.message.id }.sortedWith { left, right -> left.cursor.compareTo(right.cursor) }
    }

    /** D6 管理/TXT 原文入口；不改变普通 Reader、声音或模型的完整来源资格。 */
    suspend fun readCanonicalAll(scope: CompanionReadingScope): List<CompanionReadingRecord> = readAll(scope)

    suspend fun locate(
        scope: CompanionReadingScope,
        messageId: String,
        limit: Int = 40,
    ): CompanionReadingPage {
        require(limit in 1..200) { "阅读分页大小无效" }
        val all = readAll(scope)
        val index = all.indexOfFirst { it.message.id == messageId }
        check(index >= 0) { "原文已不可用，请刷新历史" }
        val start = maxOf(0, index - limit / 2)
        val retained = all.drop(start)
        return CompanionReadingPage(retained, retained.firstOrNull()?.cursor, start > 0)
    }
}
