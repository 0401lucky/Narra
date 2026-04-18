package com.example.myapplication.roleplay

/**
 * 沉浸式扮演的"导演视角"关键字表。
 *
 * 从 [RoleplayConversationSupport] 中抽出，便于：
 * - 扩展/调整词表不碰业务代码
 * - 单元测试传入自定义词表
 * - 后续若支持英文/繁中可在这里追加变体
 *
 * 所有匹配都经过 [normalize] 做一次轻量归一化（空白折叠、半/全角问号统一），
 * 避免因为用户输入里夹杂空格、换行或问号变体而漏匹配。
 */
internal object RoleplayDirectorKeywords {
    /** 高压对峙类：质问、逼迫、对抗。 */
    val tensionHigh: List<String> = listOf(
        "为什么", "凭什么", "到底", "说清楚", "解释",
        "你敢", "不准", "别过来", "休想", "骗",
    )

    /** 暧昧靠近类：肢体接触、温柔安抚。 */
    val tensionTender: List<String> = listOf(
        "靠近", "轻声", "抱", "握住", "吻",
        "温柔", "心疼", "安抚",
    )

    /** 动作优先类：需要角色给出即时动作反馈。 */
    val actionPriority: List<String> = listOf(
        "靠近", "后退", "伸手", "抬手", "抓住",
        "握住", "抱住", "推开", "看着", "逼近", "退开",
    )

    /** 情绪优先类：需要角色先回应对方情绪。 */
    val emotionPriority: List<String> = listOf(
        "难过", "委屈", "生气", "害怕", "紧张",
        "失望", "愤怒", "不安", "心虚", "哽咽",
    )

    /** 问号集合（半角、全角），用于疑问/追问类识别。 */
    val questionMarks: Set<Char> = setOf('？', '?')

    /** 感叹号集合（半角、全角），用于高强度情绪识别。 */
    val exclamationMarks: Set<Char> = setOf('!', '！')

    /**
     * 归一化输入文本：多个空白压缩为单个空格，尾随空白裁掉。
     * 关键字匹配时先做一次以减少误判。
     */
    fun normalize(input: String): String {
        if (input.isEmpty()) return input
        return input.trim().replace(WHITESPACE_RUN, " ")
    }

    /**
     * 判断归一化后的文本是否命中任意关键字。
     */
    fun containsAny(
        normalizedText: String,
        keywords: List<String>,
    ): Boolean {
        if (normalizedText.isEmpty() || keywords.isEmpty()) return false
        return keywords.any { it in normalizedText }
    }

    /** 文本中是否含任意问号（半/全角）。 */
    fun hasQuestionMark(text: String): Boolean {
        return text.any { it in questionMarks }
    }

    /** 文本中是否含任意感叹号（半/全角）。 */
    fun hasExclamation(text: String): Boolean {
        return text.any { it in exclamationMarks }
    }

    private val WHITESPACE_RUN = Regex("\\s+")
}
