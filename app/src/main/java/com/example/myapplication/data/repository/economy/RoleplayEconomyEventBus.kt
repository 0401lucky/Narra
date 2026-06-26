package com.example.myapplication.data.repository.economy

enum class RoleplayEconomyEventType {
    PURCHASED,
    GIFTED,
    USED,
}

data class RoleplayEconomyEvent(
    val type: RoleplayEconomyEventType,
    val itemName: String,
    val effectPrompt: String = "",
)

/**
 * 钱包操作与对话回路之间的一次性事件通道。纯内存，按 scenarioId 维护待处理事件，
 * 对话下一轮 consume 后清空，不做持久化（进程被杀后丢失一次性反应可接受）。
 */
class RoleplayEconomyEventBus {
    private val lock = Any()
    private val pending = mutableMapOf<String, MutableList<RoleplayEconomyEvent>>()

    fun post(scenarioId: String, event: RoleplayEconomyEvent) {
        if (scenarioId.isBlank()) return
        synchronized(lock) {
            pending.getOrPut(scenarioId) { mutableListOf() }.add(event)
        }
    }

    fun consume(scenarioId: String): List<RoleplayEconomyEvent> {
        if (scenarioId.isBlank()) return emptyList()
        return synchronized(lock) {
            pending.remove(scenarioId).orEmpty()
        }
    }
}

fun formatEconomyEventNote(events: List<RoleplayEconomyEvent>): String {
    if (events.isEmpty()) return ""
    return buildString {
        appendLine("【道具刚刚发生的事】")
        events.forEach { event ->
            val action = when (event.type) {
                RoleplayEconomyEventType.PURCHASED -> "买下了"
                RoleplayEconomyEventType.GIFTED -> "送给你"
                RoleplayEconomyEventType.USED -> "使用了"
            }
            append("- 用户刚刚")
            append(action)
            append("《")
            append(event.itemName)
            append("》")
            if (event.effectPrompt.isNotBlank()) {
                append("（它在剧情里的作用：")
                append(event.effectPrompt.take(120))
                append("）")
            }
            appendLine()
        }
        append("请在本次回复中自然地承接这一动作，作出符合人设的即时反应，不要无视。")
    }.trim()
}
