package com.example.myapplication.viewmodel

/**
 * 记忆自动提取的触发节流闸门（累计水位线）。
 *
 * 背景：旧逻辑用 `completedCount % window == 0` 精确取模判断是否触发提取，
 * 当 completed 计数因重试 / 删除 / 编辑 / 群聊而跳变、跨过窗口倍数点时（如 7 → 9，window=8），
 * 会永久丢失该窗口、再也不触发。改为累计水位线：记录上次触发时的 completed 计数，
 * 当“自上次提取以来新增的 completed ≥ window”即触发，并把水位线推进到当前计数。
 *
 * 仅作触发节流——记忆提取本身只看最近窗口内的消息且对已有记忆去重，
 * 故水位线无需持久化：重启后至多多触发一次幂等提取。
 */
internal class MemoryExtractionGate {
    private val lastExtractedCount = mutableMapOf<String, Int>()

    /**
     * 判断指定会话当前是否应触发记忆提取；若应触发，同时把水位线推进到 [completedCount]。
     *
     * @return true 表示应提取（且水位线已更新），false 表示跳过。
     */
    fun shouldExtract(conversationId: String, completedCount: Int, window: Int): Boolean {
        if (window <= 0 || completedCount <= 0) {
            return false
        }
        val last = lastExtractedCount[conversationId] ?: 0
        if (completedCount - last >= window) {
            lastExtractedCount[conversationId] = completedCount
            return true
        }
        return false
    }
}
