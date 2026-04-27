package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.context.ContextLogStore
import com.example.myapplication.model.ContextGovernanceSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 上下文日志 UI 状态。承载 [ContextLogStore] 的最新 15 条快照与 UI 操作。
 */
class ContextLogViewModel(
    private val store: ContextLogStore,
) : ViewModel() {

    val snapshots: StateFlow<List<ContextGovernanceSnapshot>> = store.snapshots.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = store.snapshots.value,
    )

    fun delete(id: String) {
        store.removeById(id)
    }

    fun findById(id: String): ContextGovernanceSnapshot? = store.findById(id)

    companion object {
        fun factory(store: ContextLogStore): ViewModelProvider.Factory =
            typedViewModelFactory { ContextLogViewModel(store) }
    }
}
