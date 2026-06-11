package com.example.myapplication.roleplay.script

import android.content.Context
import androidx.javascriptengine.JavaScriptSandbox
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AndroidRoleplayScriptEngine(
    private val context: Context,
    private val timeoutMillis: Long = DEFAULT_SCRIPT_TIMEOUT_MILLIS,
    private val jsonCodec: RoleplayScriptJsonCodec = RoleplayScriptJsonCodec(),
) : RoleplayScriptEngine {
    override fun isAvailable(): Boolean {
        return JavaScriptSandbox.isSupported()
    }

    override suspend fun execute(request: RoleplayScriptExecutionRequest): RoleplayScriptExecutionResult {
        if (!isAvailable()) {
            return RoleplayScriptExecutionResult(
                variables = request.input.variables,
                available = false,
                unavailableReason = "当前设备不支持 JavaScript 沙盒",
            )
        }
        val scripts = RoleplayScriptPlanner.orderedExecutableScripts(request.scripts)
        if (scripts.isEmpty()) {
            return RoleplayScriptExecutionResult(variables = request.input.variables)
        }

        return withContext(Dispatchers.Default) {
            val sandbox = JavaScriptSandbox.createConnectedInstanceAsync(context).await()
            val disabledScriptIds = mutableSetOf<String>()
            val logs = mutableListOf<RoleplayScriptLogEntry>()
            val variableUpdatesByScriptId = linkedMapOf<String, Map<String, String>>()
            var state = RoleplayScriptRuntimeState(variables = request.input.variables)
            try {
                scripts.forEach { script ->
                    val isolate = sandbox.createIsolate()
                    try {
                        val scriptInput = RoleplayScriptPermissionGate.visibleInputForScript(
                            script = script,
                            input = request.input,
                            currentVariables = state.variables,
                        )
                        val output = withTimeout(timeoutMillis) {
                            val source = buildInvocationSource(
                                event = request.event,
                                script = script,
                                input = scriptInput,
                            )
                            isolate.evaluateJavaScriptAsync(source).await()
                        }
                        val gatedOutput = RoleplayScriptPermissionGate.filterOutput(
                            script = script,
                            output = jsonCodec.decodeOutput(output),
                        )
                        if (gatedOutput.variables.isNotEmpty()) {
                            variableUpdatesByScriptId[script.id] = gatedOutput.variables
                        }
                        state = state.apply(gatedOutput)
                        if (RoleplayScriptOutputQuota.canAcceptMoreLogs(logs.size)) {
                            logs += gatedOutput.logs
                                .take(RoleplayScriptOutputQuota.remainingLogSlots(logs.size))
                                .map { message ->
                                    RoleplayScriptLogEntry(scriptId = script.id, message = message)
                                }
                        }
                    } catch (error: Throwable) {
                        disabledScriptIds += script.id
                        logs += RoleplayScriptLogEntry(
                            scriptId = script.id,
                            level = "error",
                            message = SensitiveTextRedactor.throwableMessageForUi(
                                throwable = error,
                                fallback = "脚本执行失败：${error::class.java.simpleName}",
                            ),
                        )
                    } finally {
                        isolate.close()
                    }
                }
            } finally {
                sandbox.close()
            }
            RoleplayScriptExecutionResult(
                variables = state.variables,
                variableUpdatesByScriptId = variableUpdatesByScriptId,
                promptAdditions = state.promptAdditions,
                outgoingMessage = state.outgoingMessage,
                uiDirectives = state.uiDirectives,
                logs = logs,
                disabledScriptIds = disabledScriptIds,
            )
        }
    }

    private fun buildInvocationSource(
        event: RoleplayScriptEvent,
        script: RoleplayScriptDefinition,
        input: RoleplayScriptInput,
    ): String {
        val inputLiteral = jsonCodec.toJsStringLiteral(jsonCodec.encodeInput(input))
        val eventLiteral = jsonCodec.toJsStringLiteral(event.functionName)
        val permissionsLiteral = jsonCodec.toJsStringLiteral(
            script.grantedPermissions.joinToString(separator = ",") { it.storageValue },
        )
        return """
            (function() {
              const __rpInput = JSON.parse($inputLiteral);
              const __rpEvent = $eventLiteral;
              const __rpPermissions = new Set($permissionsLiteral.split(',').filter(Boolean));
              const __rpVariables = Object.assign({}, __rpInput.variables || {});
              const __rpOutput = {
                variables: {},
                promptAdditions: [],
                outgoingMessage: null,
                uiDirectives: [],
                logs: []
              };
              const __limits = {
                variables: ${RoleplayScriptOutputQuota.MAX_VARIABLE_UPDATES_PER_SCRIPT},
                keyChars: ${RoleplayScriptOutputQuota.MAX_VARIABLE_KEY_CHARS},
                valueChars: ${RoleplayScriptOutputQuota.MAX_VARIABLE_VALUE_CHARS},
                promptAdditions: ${RoleplayScriptOutputQuota.MAX_PROMPT_ADDITIONS_PER_SCRIPT},
                promptChars: ${RoleplayScriptOutputQuota.MAX_PROMPT_ADDITION_CHARS},
                outgoingChars: ${RoleplayScriptOutputQuota.MAX_OUTGOING_MESSAGE_CHARS},
                uiDirectives: ${RoleplayScriptOutputQuota.MAX_UI_DIRECTIVES_PER_SCRIPT},
                uiTypeChars: ${RoleplayScriptOutputQuota.MAX_UI_TYPE_CHARS},
                uiPayloadChars: ${RoleplayScriptOutputQuota.MAX_UI_PAYLOAD_CHARS},
                logs: ${RoleplayScriptOutputQuota.MAX_LOGS_PER_SCRIPT},
                logChars: ${RoleplayScriptOutputQuota.MAX_LOG_CHARS}
              };
              const __has = function(name) { return __rpPermissions.has(name); };
              const __trim = function(value, limit) {
                const text = String(value == null ? "" : value).trim();
                return text.length > limit ? text.slice(0, limit) : text;
              };
              const __writeVariable = function(key, value) {
                if (!__has("write_variables")) return;
                if (Object.keys(__rpOutput.variables).length >= __limits.variables) return;
                const normalizedKey = __trim(key, __limits.keyChars);
                const normalizedValue = __trim(value, __limits.valueChars);
                if (normalizedKey) __rpOutput.variables[normalizedKey] = normalizedValue;
              };
              const __appendPrompt = function(text) {
                if (!__has("modify_prompt")) return;
                if (__rpOutput.promptAdditions.length >= __limits.promptAdditions) return;
                const normalized = __trim(text, __limits.promptChars);
                if (normalized) __rpOutput.promptAdditions.push(normalized);
              };
              const __setOutgoing = function(text) {
                if (!__has("modify_outgoing_message")) return;
                const normalized = __trim(text, __limits.outgoingChars);
                if (normalized) __rpOutput.outgoingMessage = normalized;
              };
              const __addUiDirective = function(type, payload) {
                if (!__has("render_state")) return;
                if (__rpOutput.uiDirectives.length >= __limits.uiDirectives) return;
                const normalizedType = __trim(type, __limits.uiTypeChars);
                const normalizedPayload = __trim(payload, __limits.uiPayloadChars);
                if (normalizedType) __rpOutput.uiDirectives.push({ type: normalizedType, payload: normalizedPayload });
              };
              const __addLog = function(message) {
                if (!__has("write_log")) return;
                if (__rpOutput.logs.length >= __limits.logs) return;
                const normalized = __trim(message, __limits.logChars);
                if (normalized) __rpOutput.logs.push(normalized);
              };
              const rp = {
                vars: {
                  read: function(key) {
                    return Object.prototype.hasOwnProperty.call(__rpVariables, key) ? String(__rpVariables[key]) : "";
                  },
                  write: function(key, value) {
                    __writeVariable(key, value);
                  }
                },
                prompt: {
                  append: function(text) {
                    __appendPrompt(text);
                  }
                },
                message: {
                  setOutgoingText: function(text) {
                    __setOutgoing(text);
                  }
                },
                ui: {
                  directive: function(type, payload) {
                    __addUiDirective(type, payload);
                  }
                },
                log: function(message) {
                  __addLog(message);
                }
              };
              const module = { exports: {} };
              const exports = module.exports;
              ${script.source}
              const __handler =
                (typeof globalThis[__rpEvent] === "function" && globalThis[__rpEvent]) ||
                (module.exports && typeof module.exports[__rpEvent] === "function" && module.exports[__rpEvent]) ||
                (exports && typeof exports[__rpEvent] === "function" && exports[__rpEvent]);
              if (__handler) {
                const __returned = __handler(__rpInput, rp);
                if (__returned && typeof __returned === "object") {
                  if (__returned.variables && typeof __returned.variables === "object") {
                    Object.keys(__returned.variables).forEach(function(key) {
                      __writeVariable(key, __returned.variables[key]);
                    });
                  }
                  if (Array.isArray(__returned.promptAdditions)) {
                    __returned.promptAdditions.forEach(__appendPrompt);
                  }
                  if (typeof __returned.outgoingMessage === "string") {
                    __setOutgoing(__returned.outgoingMessage);
                  }
                  if (Array.isArray(__returned.uiDirectives)) {
                    __returned.uiDirectives.forEach(function(item) {
                      if (item && typeof item === "object") __addUiDirective(item.type, item.payload);
                    });
                  }
                  if (Array.isArray(__returned.logs)) {
                    __returned.logs.forEach(__addLog);
                  }
                }
              }
              return JSON.stringify(__rpOutput);
            })()
        """.trimIndent()
    }

    companion object {
        const val DEFAULT_SCRIPT_TIMEOUT_MILLIS = 2_000L
    }
}
