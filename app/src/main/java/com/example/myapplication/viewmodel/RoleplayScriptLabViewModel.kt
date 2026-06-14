package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.roleplay.script.RoleplayScriptRepository
import com.example.myapplication.roleplay.script.DisabledRoleplayScriptEngine
import com.example.myapplication.roleplay.script.RoleplayScriptDefinition
import com.example.myapplication.roleplay.script.RoleplayScriptEngine
import com.example.myapplication.roleplay.script.RoleplayScriptEvent
import com.example.myapplication.roleplay.script.RoleplayScriptExecutionRequest
import com.example.myapplication.roleplay.script.RoleplayScriptExecutionResult
import com.example.myapplication.roleplay.script.RoleplayScriptInput
import com.example.myapplication.roleplay.script.RoleplayScriptPermission
import com.example.myapplication.roleplay.script.RoleplayScriptScope
import com.example.myapplication.roleplay.script.RoleplayScriptUiDirective
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
    val templates: List<RoleplayScriptTemplate> = RoleplayScriptTemplates.all,
    val draft: RoleplayScriptDraft = RoleplayScriptDraft(),
    val test: RoleplayScriptTestState = RoleplayScriptTestState(),
    val selectedScriptId: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
) {
    val hasSelection: Boolean
        get() = selectedScriptId.isNotBlank()
}

data class RoleplayScriptTemplate(
    val id: String,
    val title: String,
    val summary: String,
    val recommendedScope: RoleplayScriptScope,
    val source: String,
    val permissions: Set<RoleplayScriptPermission>,
)

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

data class RoleplayScriptTestState(
    val event: RoleplayScriptEvent = RoleplayScriptEvent.BEFORE_PROMPT,
    val userText: String = "我今天有点累。",
    val promptText: String = "角色正在和用户进行 RP 对话。",
    val assistantText: String = "我在听，你慢慢说。",
    val variablesText: String = "",
    val isRunning: Boolean = false,
    val result: RoleplayScriptTestResult? = null,
)

data class RoleplayScriptTestResult(
    val available: Boolean = true,
    val unavailableReason: String = "",
    val promptAdditions: List<String> = emptyList(),
    val outgoingMessage: String? = null,
    val variables: Map<String, String> = emptyMap(),
    val uiDirectives: List<RoleplayScriptUiDirective> = emptyList(),
    val logs: List<String> = emptyList(),
    val disabledScriptIds: Set<String> = emptySet(),
) {
    companion object {
        fun fromExecution(result: RoleplayScriptExecutionResult): RoleplayScriptTestResult {
            return RoleplayScriptTestResult(
                available = result.available,
                unavailableReason = result.unavailableReason,
                promptAdditions = result.promptAdditions,
                outgoingMessage = result.outgoingMessage,
                variables = result.variables,
                uiDirectives = result.uiDirectives,
                logs = result.logs.map { log -> log.message },
                disabledScriptIds = result.disabledScriptIds,
            )
        }
    }
}

class RoleplayScriptLabViewModel(
    private val scriptRepository: RoleplayScriptRepository,
    private val scriptEngine: RoleplayScriptEngine = DisabledRoleplayScriptEngine(),
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
                test = it.test.copy(result = null),
                message = null,
            )
        }
    }

    fun applyTemplate(templateId: String) {
        val template = RoleplayScriptTemplates.all.firstOrNull { it.id == templateId } ?: return
        internal.update { current ->
            current.copy(
                selectedScriptId = "",
                draft = current.draft.copy(
                    id = current.draft.id.ifBlank(idProvider),
                    name = template.title,
                    scope = template.recommendedScope,
                    ownerId = if (template.recommendedScope == RoleplayScriptScope.GLOBAL) {
                        ""
                    } else {
                        current.draft.ownerId
                    },
                    source = template.source,
                    enabled = true,
                    grantedPermissions = template.permissions,
                ),
                test = current.test.copy(
                    event = template.defaultEvent(),
                    result = null,
                ),
                message = "已套用模板：${template.title}",
            )
        }
    }

    fun selectScript(scriptId: String) {
        val script = uiState.value.scripts.firstOrNull { it.id == scriptId.trim() } ?: return
        internal.update {
            it.copy(
                selectedScriptId = script.id,
                draft = RoleplayScriptDraft.fromDefinition(script),
                test = it.test.copy(result = null),
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
        internal.update { it.copy(draft = it.draft.copy(source = source), test = it.test.copy(result = null)) }
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
                test = current.test.copy(result = null),
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
            internal.update { it.copy(message = "非全局脚本需要选择或填写绑定目标") }
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
                        test = it.test.copy(result = null),
                        message = "脚本已删除",
                    )
                } else {
                    it.copy(isSaving = false, message = "脚本删除失败")
                }
            }
        }
    }

    fun updateTestEvent(event: RoleplayScriptEvent) {
        internal.update { it.copy(test = it.test.copy(event = event, result = null)) }
    }

    fun updateTestUserText(userText: String) {
        internal.update { it.copy(test = it.test.copy(userText = userText, result = null)) }
    }

    fun updateTestPromptText(promptText: String) {
        internal.update { it.copy(test = it.test.copy(promptText = promptText, result = null)) }
    }

    fun updateTestAssistantText(assistantText: String) {
        internal.update { it.copy(test = it.test.copy(assistantText = assistantText, result = null)) }
    }

    fun updateTestVariablesText(variablesText: String) {
        internal.update { it.copy(test = it.test.copy(variablesText = variablesText, result = null)) }
    }

    fun runScriptTest() {
        val current = internal.value
        val draft = current.draft
        val source = draft.source.trim()
        if (source.isBlank()) {
            internal.update { it.copy(message = "脚本内容不能为空") }
            return
        }
        val script = draft.copy(
            id = draft.id.ifBlank(idProvider),
            name = draft.name.ifBlank { "试运行脚本" },
            source = source,
            enabled = true,
        ).toDefinition()
        val testInput = current.test
        val variables = parseTestVariables(testInput.variablesText)

        viewModelScope.launch {
            internal.update {
                it.copy(
                    test = it.test.copy(isRunning = true, result = null),
                    message = null,
                )
            }
            val result = runCatching {
                scriptEngine.execute(
                    RoleplayScriptExecutionRequest(
                        event = testInput.event,
                        input = RoleplayScriptInput(
                            sessionId = "lab-session",
                            characterId = "lab-character",
                            scenarioId = "lab-scenario",
                            userText = testInput.userText,
                            promptText = testInput.promptText,
                            assistantText = testInput.assistantText,
                            variables = variables,
                        ),
                        scripts = listOf(script),
                    ),
                )
            }.fold(
                onSuccess = RoleplayScriptTestResult::fromExecution,
                onFailure = { error ->
                    RoleplayScriptTestResult(
                        available = false,
                        unavailableReason = error.message ?: "脚本试运行失败",
                    )
                },
            )
            internal.update {
                it.copy(
                    test = it.test.copy(isRunning = false, result = result),
                    message = if (result.available) {
                        "试运行完成"
                    } else {
                        result.unavailableReason.ifBlank { "试运行不可用" }
                    },
                )
            }
        }
    }

    fun consumeMessage() {
        internal.update { it.copy(message = null) }
    }

    companion object {
        fun factory(
            scriptRepository: RoleplayScriptRepository,
            scriptEngine: RoleplayScriptEngine = DisabledRoleplayScriptEngine(),
        ): ViewModelProvider.Factory = typedViewModelFactory {
            RoleplayScriptLabViewModel(
                scriptRepository = scriptRepository,
                scriptEngine = scriptEngine,
            )
        }

        val DEFAULT_SCRIPT_SOURCE = """
function beforePrompt(input, rp) {
  rp.log("beforePrompt");
}
""".trimIndent()
    }
}

object RoleplayScriptTemplates {
    val all: List<RoleplayScriptTemplate> = listOf(
        RoleplayScriptTemplate(
            id = "style_rule",
            title = "回复风格规则",
            summary = "在生成前追加一条导演提示，适合约束语气和边界。",
            recommendedScope = RoleplayScriptScope.GLOBAL,
            permissions = setOf(RoleplayScriptPermission.MODIFY_PROMPT),
            source = """
function beforePrompt(input, rp) {
  rp.prompt.append("本轮回复要求：角色保持克制自然，不要突然表白，不要越过当前关系边界。");
}
""".trimIndent(),
        ),
        RoleplayScriptTemplate(
            id = "relationship_stage",
            title = "关系阶段推进",
            summary = "记录对话轮数，达到条件后追加不同阶段提示。",
            recommendedScope = RoleplayScriptScope.SESSION,
            permissions = setOf(
                RoleplayScriptPermission.READ_VARIABLES,
                RoleplayScriptPermission.WRITE_VARIABLES,
                RoleplayScriptPermission.MODIFY_PROMPT,
            ),
            source = """
function beforePrompt(input, rp) {
  const round = Number(rp.vars.read("round") || "0") + 1;
  rp.vars.write("round", String(round));

  if (round >= 5) {
    rp.prompt.append("关系阶段：已经有几轮交流，可以让角色表现出更熟悉但仍然自然的态度。");
  } else {
    rp.prompt.append("关系阶段：刚开始交流，角色应该保持正常距离。");
  }
}
""".trimIndent(),
        ),
        RoleplayScriptTemplate(
            id = "send_rewrite",
            title = "发送前改写",
            summary = "用户输入特定标记时，发送前自动改写文本。",
            recommendedScope = RoleplayScriptScope.GLOBAL,
            permissions = setOf(RoleplayScriptPermission.MODIFY_OUTGOING_MESSAGE),
            source = """
function beforeSend(input, rp) {
  if (input.userText.indexOf("/悄悄") >= 0) {
    rp.message.setOutgoingText(input.userText.replace("/悄悄", "（压低声音）"));
  }
}
""".trimIndent(),
        ),
        RoleplayScriptTemplate(
            id = "state_notice",
            title = "状态提示",
            summary = "进入会话或刷新状态时弹出一条状态提示。",
            recommendedScope = RoleplayScriptScope.SESSION,
            permissions = setOf(
                RoleplayScriptPermission.READ_VARIABLES,
                RoleplayScriptPermission.RENDER_STATE,
            ),
            source = """
function onSessionStart(input, rp) {
  rp.ui.directive("notice", "脚本已生效");
}

function renderState(input, rp) {
  const mood = rp.vars.read("mood") || "平稳";
  rp.ui.directive("status", "当前状态：" + mood);
}
""".trimIndent(),
        ),
    )
}

private fun RoleplayScriptTemplate.defaultEvent(): RoleplayScriptEvent {
    return when {
        "beforeSend" in source -> RoleplayScriptEvent.BEFORE_SEND
        "onSessionStart" in source -> RoleplayScriptEvent.ON_SESSION_START
        "renderState" in source -> RoleplayScriptEvent.RENDER_STATE
        else -> RoleplayScriptEvent.BEFORE_PROMPT
    }
}

private fun parseTestVariables(raw: String): Map<String, String> {
    return raw.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .mapNotNull { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) {
                null
            } else {
                val key = line.substring(0, separatorIndex).trim()
                val value = line.substring(separatorIndex + 1).trim()
                key.takeIf(String::isNotBlank)?.let { it to value }
            }
        }
        .toMap()
}
