package com.example.myapplication.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_WORLD_BOOK_MAX_ENTRIES
import com.example.myapplication.model.DEFAULT_WORLD_BOOK_SCAN_DEPTH
import com.example.myapplication.model.WORLD_BOOK_MAX_PRIMARY_KEYWORDS
import com.example.myapplication.model.WORLD_BOOK_MAX_SECONDARY_KEYWORDS
import com.example.myapplication.model.WORLD_BOOK_REGEX_MATCH_SOURCE_MAX_CHARS
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookMatchMode
import com.example.myapplication.model.expandWorldBookKeywordCandidates
import com.example.myapplication.model.isAllowedWorldBookKeyword
import com.example.myapplication.model.parseWorldBookRegexLiteral
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
            // 热路径调用方（PromptContextAssembler / SearchWorldBookTool /
            // ToolAvailabilityResolver）自 T15-D2 起已改走 DAO 的
            // listAccessibleEnabledEntries，传进来的 entries 已按 scope 过滤；
            // 这里保留防御式二次过滤，覆盖未走 DAO 的老调用方（以及单测直接
            // 构造跨 scope 入参的场景）——对已过滤集合再跑一次是 O(n) 无副作用。
            entries = entries,
            assistant = assistant,
            conversation = conversation,
        )
            .asSequence()
            .filter { it.enabled }
            .filter { entry ->
                entry.alwaysActive || hasKeywordHit(entry, sourceText)
            }
            .filter { entry -> passesProbability(entry, conversation.id) }
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
        val primaryMatched = expandKeywordPatterns(
            rawPatterns = entry.keywords + entry.aliases,
            matchMode = entry.matchMode,
            maxItems = WORLD_BOOK_MAX_PRIMARY_KEYWORDS,
        )
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
        return expandKeywordPatterns(
            rawPatterns = entry.secondaryKeywords,
            matchMode = entry.matchMode,
            maxItems = WORLD_BOOK_MAX_SECONDARY_KEYWORDS,
        )
            .any { keyword ->
                matchesPattern(
                    pattern = keyword,
                    sourceText = sourceText,
                    caseSensitive = entry.caseSensitive,
                    matchMode = entry.matchMode,
                )
            }
    }

    internal fun passesProbability(entry: WorldBookEntry, conversationId: String): Boolean {
        val probability = entry.probability.coerceIn(0, 100)
        if (probability >= 100) return true
        if (probability <= 0) return false
        val rollSeed = "${entry.id}\u0000$conversationId"
        val roll = Math.floorMod(rollSeed.hashCode(), 100)
        return roll < probability
    }

    /**
     * 给编辑页"试命中"即时反馈用：不走 scope 过滤与 maxEntries 上限，
     * 只针对给定的待测文本计算 primary / secondary 的命中情况。
     *
     * 语义必须和 [match] 内部一致：Matcher 实际判定是
     * `entry.alwaysActive || hasKeywordHit(entry, sourceText)`，因此：
     *
     * - 待测文本为空 → miss（不跑任何匹配）
     * - alwaysActive=true → 恒为 matched=true；primary/secondary 仍按实际匹配填入，
     *   让用户看到"如果关掉 alwaysActive 会不会命中"；**不会**因为 selective
     *   次级未命中而退回 miss。
     * - alwaysActive=false：走关键词命中链路；primary 未命中则 miss；selective
     *   开启且次级未命中则 miss。
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
        val primaryHits = expandKeywordPatterns(
            rawPatterns = entry.keywords + entry.aliases,
            matchMode = entry.matchMode,
            maxItems = WORLD_BOOK_MAX_PRIMARY_KEYWORDS,
        )
            .filter { pattern ->
                matchesPattern(
                    pattern = pattern,
                    sourceText = sourceText,
                    caseSensitive = entry.caseSensitive,
                    matchMode = entry.matchMode,
                )
            }
            .distinct()
        val secondaryHits = if (entry.selective && entry.secondaryKeywords.isNotEmpty()) {
            expandKeywordPatterns(
                rawPatterns = entry.secondaryKeywords,
                matchMode = entry.matchMode,
                maxItems = WORLD_BOOK_MAX_SECONDARY_KEYWORDS,
            )
                .filter { pattern ->
                    matchesPattern(
                        pattern = pattern,
                        sourceText = sourceText,
                        caseSensitive = entry.caseSensitive,
                        matchMode = entry.matchMode,
                    )
                }
                .distinct()
        } else {
            emptyList()
        }

        if (entry.alwaysActive) {
            // Matcher 里 alwaysActive 与关键词命中是 OR 关系，alwaysActive=true
            // 直接覆盖 selective / secondary 检查。
            return WorldBookHitPreview(
                overallMatched = true,
                primaryHits = primaryHits,
                secondaryHits = secondaryHits,
            )
        }

        if (primaryHits.isEmpty()) {
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
        maxItems: Int,
    ): List<String> {
        val expanded = mutableListOf<String>()
        for (rawPattern in rawPatterns) {
            if (expanded.size >= maxItems) break
            for (candidate in expandWorldBookKeywordCandidates(rawPattern, matchMode)) {
                if (expanded.size >= maxItems) break
                val normalized = candidate.trim()
                if (normalized.isNotEmpty() && isAllowedWorldBookKeyword(normalized, matchMode)) {
                    expanded += normalized
                }
            }
        }
        return expanded
    }

    private fun matchesPattern(
        pattern: String,
        sourceText: String,
        caseSensitive: Boolean,
        matchMode: WorldBookMatchMode,
    ): Boolean {
        val regexSourceText = sourceText.takeLast(WORLD_BOOK_REGEX_MATCH_SOURCE_MAX_CHARS)
        // /.../ 字面量语法跨 matchMode 保留 escape hatch，优先识别。
        parseRegexLiteral(pattern, caseSensitive = caseSensitive)?.let { regex ->
            return regex.containsMatchIn(regexSourceText)
        }
        return when (matchMode) {
            WorldBookMatchMode.CONTAINS -> sourceText.contains(pattern, ignoreCase = !caseSensitive)
            WorldBookMatchMode.WORD_CJK -> matchesContainsCjkAware(pattern, sourceText, caseSensitive)
            WorldBookMatchMode.REGEX -> {
                val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                buildSafeRegex(pattern, options)
                    ?.containsMatchIn(regexSourceText)
                    ?: false
            }
        }
    }

    private fun buildSafeRegex(pattern: String, options: Set<RegexOption>): Regex? {
        if (!isAllowedWorldBookKeyword(pattern, WorldBookMatchMode.REGEX)) {
            return null
        }
        return runCatching { Regex(pattern, options) }
            .logFailure("WorldBookMatcher") { "compile keyword regex failed" }
            .getOrNull()
    }

    private fun parseRegexLiteral(pattern: String, caseSensitive: Boolean = true): Regex? {
        val literal = parseWorldBookRegexLiteral(pattern) ?: return null
        val options = mutableSetOf<RegexOption>()
        for (flag in literal.flags) {
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
        return buildSafeRegex(literal.body, options)
    }
}
