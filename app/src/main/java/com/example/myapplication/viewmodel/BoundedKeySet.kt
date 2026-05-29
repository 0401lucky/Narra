package com.example.myapplication.viewmodel

/**
 * 固定容量的字符串键集合：超出容量时按插入顺序淘汰最早写入的键。
 *
 * 用于在线补偿"空结果抑制键"集合（[RoleplayViewModel.maybeTriggerOnlineCompensation]），
 * 避免长会话期间随消息持续累积而无界增长。调用方以 `key in set` / `set += key` 使用，
 * 与原 [MutableSet] 用法保持一致。
 */
internal class BoundedKeySet(private val maxSize: Int) {
    init {
        require(maxSize > 0) { "maxSize 必须为正数" }
    }

    private val backing = object : LinkedHashMap<String, Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>): Boolean {
            return size > maxSize
        }
    }

    val size: Int
        get() = backing.size

    operator fun contains(key: String): Boolean = backing.containsKey(key)

    operator fun plusAssign(key: String) {
        backing[key] = Unit
    }
}
