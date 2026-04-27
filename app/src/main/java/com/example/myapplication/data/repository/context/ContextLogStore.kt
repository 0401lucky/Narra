package com.example.myapplication.data.repository.context

import com.example.myapplication.model.CONTEXT_LOG_CAPACITY_MAX
import com.example.myapplication.model.CONTEXT_LOG_CAPACITY_MIN
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.DEFAULT_CONTEXT_LOG_CAPACITY
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 上下文日志内存存储。维护一个容量可由用户配置的环形缓冲区，按写入时间倒序（最新在前）暴露给 UI 订阅。
 *
 * Chat / Roleplay / Memory 三类请求都向此 store 写入，UI 通过 [snapshots] 订阅。
 *
 * 设计选择：仅在内存中保留，进程重启即丢失（这是调试工具，重启丢失可接受），
 * 避免 Room schema 升级 + ContextLogSection 序列化两笔成本。
 *
 * 容量与启停由 [setCapacity] / [setEnabled] 配置；通常由 AppGraph 订阅 AppSettings 流后同步调用，
 * 因此本类对配置源保持解耦。
 */
class ContextLogStore {

    private val mutableSnapshots = MutableStateFlow<List<ContextGovernanceSnapshot>>(emptyList())

    val snapshots: StateFlow<List<ContextGovernanceSnapshot>> = mutableSnapshots.asStateFlow()

    @Volatile
    private var capacity: Int = DEFAULT_CONTEXT_LOG_CAPACITY

    @Volatile
    private var enabled: Boolean = true

    /** 在请求发起前调用一次，把 snapshot 推入列表头部，超过上限自动剔除最旧的一条；当 enabled=false 时整体禁用写入。 */
    fun push(snapshot: ContextGovernanceSnapshot) {
        if (!enabled) return
        val effectiveCapacity = capacity
        mutableSnapshots.update { current ->
            val deduped = current.filterNot { it.id == snapshot.id }
            (listOf(snapshot) + deduped).take(effectiveCapacity)
        }
    }

    /** 删除指定 id 的快照。 */
    fun removeById(id: String) {
        mutableSnapshots.update { current -> current.filterNot { it.id == id } }
    }

    /** 清空所有快照。 */
    fun clear() {
        mutableSnapshots.value = emptyList()
    }

    fun findById(id: String): ContextGovernanceSnapshot? {
        return mutableSnapshots.value.firstOrNull { it.id == id }
    }

    /** 容量变更：调小时立即截断已有快照。 */
    fun setCapacity(value: Int) {
        val coerced = value.coerceIn(CONTEXT_LOG_CAPACITY_MIN, CONTEXT_LOG_CAPACITY_MAX)
        capacity = coerced
        mutableSnapshots.update { current -> current.take(coerced) }
    }

    /** 启停：禁用后 push() 直接 return，已有日志保留以便用户回看。 */
    fun setEnabled(value: Boolean) {
        enabled = value
    }
}
