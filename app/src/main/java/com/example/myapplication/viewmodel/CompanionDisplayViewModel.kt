package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.roleplay.CompanionResourcesRepository
import com.example.myapplication.model.*
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

data class CompanionDisplayUiState(
    val mode: CompanionDisplayMode = CompanionDisplayMode.LIST,
    val visuals: CompanionVisualSettings = CompanionVisualSettings(),
    val resources: CompanionResolvedResources = CompanionResolvedResources(),
    val isSavingPreference: Boolean = false,
    val errorMessage: String? = null,
)

/** 本地显示配置不持有请求协调器。保存失败时本次选择仍可使用，旧持久偏好保留。 */
class CompanionDisplayViewModel(
    private var scenario: RoleplayScenario,
    private val settingsStore: SettingsStore,
    private val resourcesRepository: CompanionResourcesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CompanionDisplayUiState())
    val uiState = mutableState.asStateFlow()
    private var settings = AppSettings()
    private var localMode: CompanionDisplayMode? = null
    private var resolveJob: Job? = null
    private var ownerGeneration = 0L
    private var resolveGeneration = 0L

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { latest ->
                settings = latest
                if (latest.resolvedAssistants().none { it.id == scenario.assistantId }) {
                    ownerGeneration++
                    resolveGeneration++
                    resolveJob?.cancel()
                    localMode = null
                    mutableState.value =
                        CompanionDisplayUiState(errorMessage = "角色已不可用，演出已停止。")
                    return@collect
                }
                val visuals = latest.companionSettings(scenario.assistantId).visuals
                mutableState.update {
                    it.copy(
                        mode = localMode ?: visuals.displayMode,
                        visuals = visuals
                    )
                }
                resolve()
            }
        }
    }

    fun refresh(currentScenario: RoleplayScenario = scenario) {
        if (currentScenario.id != scenario.id || currentScenario.assistantId != scenario.assistantId) {
            ownerGeneration++
            localMode = null
            val visuals = settings.companionSettings(currentScenario.assistantId).visuals
            mutableState.value =
                CompanionDisplayUiState(mode = visuals.displayMode, visuals = visuals)
        }
        scenario = currentScenario
        resolve()
    }

    fun chooseMode(mode: CompanionDisplayMode) {
        if (mutableState.value.isSavingPreference) return
        if (settings.resolvedAssistants().none { it.id == scenario.assistantId }) return
        val ownerId = scenario.assistantId
        val generation = ownerGeneration
        val current = settings.companionSettings(ownerId)
        localMode = mode
        mutableState.update { it.copy(mode = mode, isSavingPreference = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                if (generation != ownerGeneration) return@launch
                val result = settingsStore.updateCompanionSettings(
                    ownerId, current.revision,
                    CompanionSettingsPatch(visuals = current.visuals.copy(preferredMode = mode))
                )
                coroutineContext.ensureActive()
                if (generation != ownerGeneration) return@launch
                if (result is CompanionSettingsWriteResult.Saved) {
                    localMode = null
                    mutableState.update {
                        it.copy(
                            mode = result.settings.visuals.displayMode,
                            visuals = result.settings.visuals
                        )
                    }
                } else mutableState.update {
                    it.copy(
                        errorMessage = when (result) {
                            CompanionSettingsWriteResult.Conflict -> "显示已切换，偏好已在别处更新；可重新选择后保存。"
                            CompanionSettingsWriteResult.CharacterMissing -> "角色已不可用，显示偏好未保存。"
                            else -> "显示已切换，此设置版本暂不能保存偏好。"
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == ownerGeneration) mutableState.update {
                    it.copy(
                        errorMessage =
                            SensitiveTextRedactor.throwableMessageForUi(
                                failure,
                                "显示已切换，偏好保存失败。"
                            )
                    )
                }
            } finally {
                if (generation == ownerGeneration) mutableState.update { it.copy(isSavingPreference = false) }
            }
        }
    }

    private fun resolve() {
        resolveJob?.cancel()
        val generation = ++resolveGeneration
        val sourceScenario = scenario
        val visuals = mutableState.value.visuals
        val assistant =
            settings.resolvedAssistants().firstOrNull { it.id == sourceScenario.assistantId }
        if (assistant == null) return
        resolveJob = viewModelScope.launch {
            try {
                val resolved = resourcesRepository.resolve(visuals, sourceScenario, assistant)
                coroutineContext.ensureActive()
                if (generation != resolveGeneration || scenario != sourceScenario || mutableState.value.visuals != visuals) return@launch
                mutableState.update { it.copy(resources = resolved) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == resolveGeneration) mutableState.update {
                    it.copy(
                        resources = CompanionResolvedResources(),
                        errorMessage = "演出图片暂不可读，仍可阅读正文。"
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            scenario: RoleplayScenario,
            settingsStore: SettingsStore,
            resources: CompanionResourcesRepository
        ) =
            typedViewModelFactory { CompanionDisplayViewModel(scenario, settingsStore, resources) }
    }
}
