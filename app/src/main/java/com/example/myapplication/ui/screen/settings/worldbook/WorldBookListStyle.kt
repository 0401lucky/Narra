package com.example.myapplication.ui.screen.settings.worldbook

import androidx.compose.ui.graphics.Color
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType

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
