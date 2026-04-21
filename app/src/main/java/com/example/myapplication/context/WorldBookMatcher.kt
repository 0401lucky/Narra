package com.example.myapplication.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_WORLD_BOOK_MAX_ENTRIES
import com.example.myapplication.model.DEFAULT_WORLD_BOOK_SCAN_DEPTH
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookMatchMode
import com.example.myapplication.system.logging.logFailure

data class WorldBookMatchResult(
    val entries: List<WorldBookEntry>,
    val sourceText: String,
)

/**
 * 编辑页"试命中"用的轻量预览：不关心 scope / alwaysActive 上限，
 * 只告诉调用方针对给定的待测文本，这条 entry 能否命中以及是哪个关键词命中。
 */
data class WorldBookHitPreview(
    val overallMatched: Boolean,
    val primaryHits: List<String>,
    val secondaryHits: List<String>,
    val reasonIfNotMatched: String? = null,
)

class WorldBookMatcher {
    fun match(
        entries: List<WorldBookEntry>,
        assistant: Assistant?,
        conversation: Conversation,
        userInputText: String,
        recentMessages: List<ChatMessage>,
    ): WorldBookMatchResult {
        val scanDepth = assistant?.worldBookScanDepth
            ?.coerceAtLeast(0)
            ?: DEFAULT_WORLD_BOOK_SCAN_DEPTH
        val sourceText = buildWorldBookSourceText(
            userInputText = userInputText,
            recentMessages = recentMessages,
            scanDepth = scanDepth,
        )
        val maxEntries = assistant?.worldBookMaxEntries
            ?.takeIf { it > 0 }
            ?: DEFAULT_WORLD_BOOK_MAX_ENTRIES

        val matchedEntries = WorldBookScopeSupport.filterAccessibleEntries(
            entries = entries,
            assistant = assistant,
            conversation = conversation,
        )
            .asSequence()
            .filter { it.enabled }
            .filter { entry ->
                entry.alwaysActive || hasKeywordHit(entry, sourceText)
            }
            .sortedWith(WorldBookScopeSupport.priorityComparator())
            .take(maxEntries)
            .toList()

        return WorldBookMatchResult(
            entries = matchedEntries,
            sourceText = sourceText,
        )
    }

    private fun hasKeywordHit(
        entry: WorldBookEntry,
        sourceText: String,
    ): Boolean {
        if (sourceText.isBlank()) {
            return false
        }
        val primaryMatched = expandKeywordPatterns(entry.keywords + entry.aliases, entry.matchMode)
            .any { keyword ->
                matchesPattern(
                    pattern = keyword,
                    sourceText = sourceText,
                    caseSensitive = entry.caseSensitive,
                    matchMode = entry.matchMode,
                )
            }
        if (!primaryMatched) {
            return false
        }
        if (!entry.selective || entry.secondaryKeywords.isEmpty()) {
            return true
        }
        return expandKeywordPatterns(entry.secondaryKeywords, entry.matchMode)
            .any { keyword ->
                matchesPattern(
                    pattern = keyword,
                    sourceText = sourceText,
                    caseSensitive = entry.caseSensitive,
                    matchMode = entry.matchMode,
                )
            }
    }

    /**
     * 给编辑页"试命中"即时反馈用：不走 scope / alwaysActive 上限过滤，
     * 只针对给定的待测文本计算 primary / secondary 的命中情况。
     *
     * - 待测文本为空 → miss，给出提示语
     * - primary 未命中且 alwaysActive=false → miss，reason 说明
     * - alwaysActive=true → 视为 overallMatched=true（即使 primary 未命中），
     *   primaryHits 仍按实际匹配列出
     * - selective=true 但次级关键词未命中 → miss，解释 selective 的要求
     */
    fun previewHit(entry: WorldBookEntry, sourceText: String): WorldBookHitPreview {
        if (sourceText.isBlank()) {
            return WorldBookHitPreview(
                overallMatched = false,
                primaryHits = emptyList(),
                secondaryHits = emptyList(),
                reasonIfNotMatched = "待测文本为空",
            )
        }
        val primaryHits = expandKeywordPatterns(entry.keywords + entry.aliases, entry.matchMode)
            .filter { pattern ->
                matchesPattern(
                    pattern = pattern,
                    sourceText = sourceText,
                    caseSensitive = entry.caseSensitive,
                    matchMode = entry.matchMode,
                )
            }
            .distinct()

        if (primaryHits.isEmpty() && !entry.alwaysActive) {
            return WorldBookHitPreview(
                overallMatched = false,
                primaryHits = emptyList(),
                secondaryHits = emptyList(),
                reasonIfNotMatched = "主关键词均未命中",
            )
        }

        if (!entry.selective || entry.secondaryKeywords.isEmpty()) {
            return WorldBookHitPreview(
                overallMatched = true,
                primaryHits = primaryHits,
                secondaryHits = emptyList(),
            )
        }

        val secondaryHits = expandKeywordPatterns(entry.secondaryKeywords, entry.matchMode)
            .filter { pattern ->
                matchesPattern(
                    pattern = pattern,
                    sourceText = sourceText,
                    caseSensitive = entry.caseSensitive,
                    matchMode = entry.matchMode,
                )
            }
            .distinct()

        return if (secondaryHits.isEmpty()) {
            WorldBookHitPreview(
                overallMatched = false,
                primaryHits = primaryHits,
                secondaryHits = emptyList(),
                reasonIfNotMatched = "主关键词已命中，但次级关键词未命中（selective 开启）",
            )
        } else {
            WorldBookHitPreview(
                overallMatched = true,
                primaryHits = primaryHits,
                secondaryHits = secondaryHits,
            )
        }
    }

    private fun expandKeywordPatterns(
        rawPatterns: List<String>,
        matchMode: WorldBookMatchMode,
    ): List<String> {
        return rawPatterns.flatMap { rawPattern ->
            val normalized = rawPattern.trim()
            when {
                normalized.isBlank() -> emptyList()
                // REGEX 模式下：整条 keyword 直接作为正则，不按逗号拆
                matchMode == WorldBookMatchMode.REGEX -> listOf(normalized)
                parseRegexLiteral(normalized) != null -> listOf(normalized)
                else -> normalized
                    .split(KeywordDelimiterRegex)
                    .mapNotNull { token ->
                        token.trim().takeIf { it.isNotEmpty() }
                    }
            }
        }
    }

    private fun matchesPattern(
        pattern: String,
        sourceText: String,
        caseSensitive: Boolean,
        matchMode: WorldBookMatchMode,
    ): Boolean {
        // /.../ 字面量语法跨 matchMode 保留 escape hatch，优先识别
        parseRegexLiteral(pattern, caseSensitive = caseSensitive)?.let { regex ->
            return regex.containsMatchIn(sourceText)
        }
        return when (matchMode) {
            WorldBookMatchMode.CONTAINS -> sourceText.contains(pattern, ignoreCase = !caseSensitive)
            WorldBookMatchMode.WORD_CJK -> matchesContainsCjkAware(pattern, sourceText, caseSensitive)
            WorldBookMatchMode.REGEX -> {
                val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                runCatching { Regex(pattern, options) }
                    .logFailure("WorldBookMatcher") { "compile keyword regex failed: $pattern" }
                    .getOrNull()
                    ?.containsMatchIn(sourceText)
                    ?: false
            }
        }
    }

    private fun parseRegexLiteral(pattern: String, caseSensitive: Boolean = true): Regex? {
        if (pattern.length < 2 || !pattern.startsWith('/')) {
            return null
        }
        var escaped = false
        var endIndex = -1
        for (index in 1 until pattern.length) {
            val current = pattern[index]
            if (current == '/' && !escaped) {
                endIndex = index
                break
            }
            escaped = current == '\\' && !escaped
            if (current != '\\') {
                escaped = false
            }
        }
        if (endIndex <= 1) {
            return null
        }
        val body = pattern.substring(1, endIndex)
        val flags = pattern.substring(endIndex + 1)
        val options = mutableSetOf<RegexOption>()
        for (flag in flags) {
            when (flag.lowercaseChar()) {
                'i' -> options.add(RegexOption.IGNORE_CASE)
                'm' -> options.add(RegexOption.MULTILINE)
                's' -> options.add(RegexOption.DOT_MATCHES_ALL)
                'g', 'u' -> Unit
                else -> return null
            }
        }
        if (!caseSensitive && RegexOption.IGNORE_CASE !in options) {
            options.add(RegexOption.IGNORE_CASE)
        }
        return runCatching { Regex(body, options) }
            .logFailure("WorldBookMatcher") { "parseRegexLiteral failed for pattern=$pattern" }
            .getOrNull()
    }

    private companion object {
        val KeywordDelimiterRegex = Regex("""\s*[,，]\s*""")
    }
}
