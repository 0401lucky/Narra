package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.roleplay.script.RoleplayScriptRepository
import com.example.myapplication.roleplay.script.RoleplayScriptDefinition
import com.example.myapplication.roleplay.script.RoleplayScriptPermission
import com.example.myapplication.roleplay.script.RoleplayScriptScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class RoleplayScriptLabUiState(
    val scripts: List<RoleplayScriptDefinition> = emptyList(),
    val draft: RoleplayScriptDraft = RoleplayScriptDraft(),
    val selectedScriptId: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
) {
    val hasSelection: Boolean
        get() = selectedScriptId.isNotBlank()
}

data class RoleplayScriptDraft(
    val id: String = "",
    val name: String = "",
    val scope: RoleplayScriptScope = RoleplayScriptScope.SESSION,
    val ownerId: String = "",
    val source: String = "",
    val enabled: Boolean = true,
    val grantedPermissions: Set<RoleplayScriptPermission> = emptySet(),
    val updatedAt: Long = 0L,
) {
    fun toDefinition(): RoleplayScriptDefinition {
        return RoleplayScriptDefinition(
            id = id.trim(),
            name = name.trim(),
            scope = scope,
            ownerId = ownerId.trim(),
            source = source,
            enabled = enabled,
            grantedPermissions = grantedPermissions,
            updatedAt = updatedAt,
        )
    }

    companion object {
        fun fromDefinition(script: RoleplayScriptDefinition): RoleplayScriptDraft {
            return RoleplayScriptDraft(
                id = script.id,
                name = script.name,
                scope = script.scope,
                ownerId = script.ownerId,
                source = script.source,
                enabled = script.enabled,
                grantedPermissions = script.grantedPermissions,
                updatedAt = script.updatedAt,
            )
        }
    }
}

class RoleplayScriptLabViewModel(
    private val scriptRepository: RoleplayScriptRepository,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val internal = MutableStateFlow(RoleplayScriptLabUiState())

    val uiState: StateFlow<RoleplayScriptLabUiState> = combine(
        scriptRepository.observeScripts(),
        internal,
    ) { scripts, state ->
        state.copy(
            scripts = scripts.sortedWith(
                compareBy<RoleplayScriptDefinition> { it.scope.executionOrder }
                    .thenBy { it.ownerId }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.name.lowercase() },
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoleplayScriptLabUiState(),
    )

    init {
        createScript()
    }

    fun createScript() {
        internal.update {
            it.copy(
                selectedScriptId = "",
                draft = RoleplayScriptDraft(
                    id = idProvider(),
                    name = "新脚本",
                    scope = RoleplayScriptScope.SESSION,
                    source = DEFAULT_SCRIPT_SOURCE,
                    grantedPermissions = setOf(
                        RoleplayScriptPermission.READ_VARIABLES,
                        RoleplayScriptPermission.WRITE_LOG,
                    ),
                ),
                message = null,
            )
        }
    }

    fun selectScript(scriptId: String) {
        val script = uiState.value.scripts.firstOrNull { it.id == scriptId.trim() } ?: return
        internal.update {
            it.copy(
                selectedScriptId = script.id,
                draft = RoleplayScriptDraft.fromDefinition(script),
                message = null,
            )
        }
    }

    fun updateName(name: String) {
        internal.update { it.copy(draft = it.draft.copy(name = name)) }
    }

    fun updateScope(scope: RoleplayScriptScope) {
        internal.update { current ->
            current.copy(
                draft = current.draft.copy(
                    scope = scope,
                    ownerId = if (scope == RoleplayScriptScope.GLOBAL) "" else current.draft.ownerId,
                ),
            )
        }
    }

    fun updateOwnerId(ownerId: String) {
        internal.update { it.copy(draft = it.draft.copy(ownerId = ownerId)) }
    }

    fun updateSource(source: String) {
        internal.update { it.copy(draft = it.draft.copy(source = source)) }
    }

    fun updateEnabled(enabled: Boolean) {
        internal.update { it.copy(draft = it.draft.copy(enabled = enabled)) }
    }

    fun togglePermission(permission: RoleplayScriptPermission) {
        internal.update { current ->
            val permissions = current.draft.grantedPermissions
            current.copy(
                draft = current.draft.copy(
                    grantedPermissions = if (permission in permissions) {
                        permissions - permission
                    } else {
                        permissions + permission
                    },
                ),
            )
        }
    }

    fun saveScript() {
        val draft = internal.value.draft
        val name = draft.name.trim()
        val source = draft.source.trim()
        if (name.isBlank() || source.isBlank()) {
            internal.update { it.copy(message = "脚本名称和内容不能为空") }
            return
        }
        if (draft.scope != RoleplayScriptScope.GLOBAL && draft.ownerId.trim().isBlank()) {
            internal.update { it.copy(message = "非全局脚本需要填写绑定 ID") }
            return
        }
        viewModelScope.launch {
            internal.update { it.copy(isSaving = true, message = null) }
            val outcome = runCatching {
                scriptRepository.upsertScript(
                    draft.copy(
                        name = name,
                        source = source,
                    ).toDefinition(),
                )
            }
            internal.update {
                it.copy(
                    isSaving = false,
                    selectedScriptId = if (outcome.isSuccess) draft.id else it.selectedScriptId,
                    message = if (outcome.isSuccess) "脚本已保存" else "脚本保存失败",
                )
            }
        }
    }

    fun deleteSelectedScript() {
        val scriptId = internal.value.selectedScriptId.trim()
        if (scriptId.isBlank()) {
            return
        }
        viewModelScope.launch {
            internal.update { it.copy(isSaving = true, message = null) }
            val outcome = runCatching { scriptRepository.deleteScript(scriptId) }
            internal.update {
                if (outcome.isSuccess) {
                    it.copy(
                        isSaving = false,
                        selectedScriptId = "",
                        draft = RoleplayScriptDraft(
                            id = idProvider(),
                            name = "新脚本",
                            scope = RoleplayScriptScope.SESSION,
                            source = DEFAULT_SCRIPT_SOURCE,
                            grantedPermissions = setOf(
                                RoleplayScriptPermission.READ_VARIABLES,
                                RoleplayScriptPermission.WRITE_LOG,
                            ),
                        ),
                        message = "脚本已删除",
                    )
                } else {
                    it.copy(isSaving = false, message = "脚本删除失败")
                }
            }
        }
    }

    fun consumeMessage() {
        internal.update { it.copy(message = null) }
    }

    companion object {
        fun factory(
            scriptRepository: RoleplayScriptRepository,
        ): ViewModelProvider.Factory = typedViewModelFactory {
            RoleplayScriptLabViewModel(scriptRepository)
        }

        val DEFAULT_SCRIPT_SOURCE = """
function beforePrompt(input, rp) {
  rp.log("beforePrompt");
}
""".trimIndent()
    }
}
