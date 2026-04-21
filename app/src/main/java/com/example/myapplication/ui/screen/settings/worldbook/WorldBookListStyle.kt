package com.example.myapplication.ui.screen.settings.worldbook

import androidx.compose.ui.graphics.Color
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.ui.component.worldbook.looksLikeWorldBookRegexLiteral
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * 世界书列表页的纯函数 / 枚举集合。
 *
 * 承担职责：
 * - 书脊色（`bookSpineColor`）
 * - 条目关键词"真词"提取（`firstRealKeywords`）
 * - 相对时间（`formatRelativeTime`）
 * - 作用域/状态筛选器与组合（`filterEntries` / `activeFilterCount`）
 *
 * 所有函数都设计为可在 JVM 单元测试中直接覆盖。
 */

/**
 * 根据 bookId 生成稳定的书脊色：HSL 色相轮转，饱和度/亮度固定。
 * 同一个 bookId 永远返回同样颜色，不同 bookId 基本不会碰撞。
 */
internal fun bookSpineColor(bookId: String): Color {
    val hue = (bookId.hashCode().absoluteValue % 360).toFloat()
    return Color.hsl(hue = hue, saturation = 0.52f, lightness = 0.62f)
}

/**
 * 从 keywords + aliases + secondaryKeywords 中按顺序提取去重、非空的前 N 个真词。
 *
 * 正则字面量（形如 `/foo,bar/i`）作为整体保留，不被拆解。
 */
internal fun firstRealKeywords(entry: WorldBookEntry, limit: Int = 3): List<String> {
    if (limit <= 0) return emptyList()
    val seen = LinkedHashSet<String>()
    val sources = sequence {
        yieldAll(entry.keywords)
        yieldAll(entry.aliases)
        yieldAll(entry.secondaryKeywords)
    }
    for (raw in sources) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) continue
        seen.add(trimmed)
        if (seen.size >= limit) break
    }
    return seen.toList()
}

/**
 * 相对时间文案：刚刚 / N 分钟前 / N 小时前 / N 天前 / yyyy-MM-dd。
 *
 * 为了可测试，允许注入 `now`；默认读取系统时钟。
 */
internal fun formatRelativeTime(
    epochMillis: Long,
    now: Long = System.currentTimeMillis(),
): String {
    if (epochMillis <= 0L) return ""
    val diff = now - epochMillis
    if (diff < 60_000L) return "刚刚"
    return when {
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        diff < 7L * 86_400_000L -> "${diff / 86_400_000L} 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMillis))
    }
}

/**
 * 列表页作用域筛选项。每项互斥，AND 与其他筛选维度组合。
 */
internal enum class WorldBookListScopeFilter(val label: String, val scope: WorldBookScopeType?) {
    ALL("全部", null),
    GLOBAL("全局", WorldBookScopeType.GLOBAL),
    ATTACHABLE("可挂载", WorldBookScopeType.ATTACHABLE),
    ASSISTANT("助手", WorldBookScopeType.ASSISTANT),
    CONVERSATION("会话", WorldBookScopeType.CONVERSATION),
}

/**
 * 列表页状态筛选项。每项互斥，AND 与其他筛选维度组合。
 */
internal enum class WorldBookListStatusFilter(val label: String) {
    ALL("全部"),
    ENABLED("启用"),
    DISABLED("停用"),
    ALWAYS_ACTIVE("常驻注入"),
    HAS_REGEX("含正则");

    fun matches(entry: WorldBookEntry): Boolean = when (this) {
        ALL -> true
        ENABLED -> entry.enabled
        DISABLED -> !entry.enabled
        ALWAYS_ACTIVE -> entry.alwaysActive
        HAS_REGEX -> (entry.keywords + entry.aliases + entry.secondaryKeywords)
            .any(::looksLikeWorldBookRegexLiteral)
    }
}

/**
 * 三维筛选 + 全文搜索的 AND 组合。所有维度在默认值时视为"不筛"，
 * 返回原列表顺序保留。
 */
internal fun filterEntries(
    entries: List<WorldBookEntry>,
    search: String,
    scope: WorldBookListScopeFilter,
    status: WorldBookListStatusFilter,
    bookIdFilter: String,
): List<WorldBookEntry> {
    val normalizedSearch = search.trim().lowercase(Locale.getDefault())
    return entries.filter { entry ->
        val matchesScope = scope == WorldBookListScopeFilter.ALL || scope.scope == entry.scopeType
        val matchesStatus = status.matches(entry)
        val matchesBook = bookIdFilter.isBlank() || entry.resolvedBookId() == bookIdFilter
        val matchesSearch = normalizedSearch.isBlank() || run {
            val haystack = buildString {
                append(entry.title).append(' ')
                append(entry.content).append(' ')
                append(entry.sourceBookName).append(' ')
                (entry.keywords + entry.aliases + entry.secondaryKeywords)
                    .forEach { append(it).append(' ') }
            }.lowercase(Locale.getDefault())
            haystack.contains(normalizedSearch)
        }
        matchesScope && matchesStatus && matchesBook && matchesSearch
    }
}

/**
 * 当前激活的筛选维度数量，用于"清筛选"按钮是否显示。
 */
internal fun activeFilterCount(
    scope: WorldBookListScopeFilter,
    status: WorldBookListStatusFilter,
    bookIdFilter: String,
): Int {
    var n = 0
    if (scope != WorldBookListScopeFilter.ALL) n++
    if (status != WorldBookListStatusFilter.ALL) n++
    if (bookIdFilter.isNotBlank()) n++
    return n
}
