package com.example.myapplication.model

import java.io.Serializable
import java.util.Locale

enum class CompanionWritingStyle(val label: String) {
    CINEMATIC("电影感"), PLAIN("简洁白描"), LITERARY("细腻文艺"),
    LIGHT_HUMOR("轻快幽默"), INTENSE("浓烈炽热"),
}

enum class CompanionPointOfView(val label: String) {
    THIRD_NAME("双方称名字"), THIRD_YOU("角色称名字，用户称你"), FIRST_YOU("角色称我，用户称你"),
}

enum class CompanionObservationStyle(val label: String) {
    CONCISE("简要"), DETAILED("细致"),
}

data class CompanionStyleTemplate(
    val id: String,
    val name: String,
    val text: String,
) : Serializable

data class CompanionObservationField(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
) : Serializable

fun companionBuiltinObservationFields(): List<CompanionObservationField> = listOf(
    CompanionObservationField("time", "时间"),
    CompanionObservationField("place", "地点"),
    CompanionObservationField("state", "状态"),
    CompanionObservationField("detail", "细节"),
)

data class CompanionObservationSettings(
    val enabled: Boolean = false,
    val style: CompanionObservationStyle = CompanionObservationStyle.CONCISE,
    val fields: List<CompanionObservationField> = companionBuiltinObservationFields(),
    val customFields: List<CompanionObservationField> = emptyList(),
) : Serializable {
    fun enabledFields(): List<CompanionObservationField> =
        (fields + customFields).filter { it.enabled }
}

data class CompanionWritingSettings(
    val style: CompanionWritingStyle? = null,
    val pointOfView: CompanionPointOfView? = null,
    val extraStyle: String = "",
    val templates: List<CompanionStyleTemplate> = emptyList(),
    val detailHintsEnabled: Boolean = true,
    val observation: CompanionObservationSettings = CompanionObservationSettings(),
) : Serializable

data class CompanionDetailHint(val id: String, val text: String) : Serializable

/** 此快照记录实际生成选择；descriptor.settingsRevision 仍单独校验本次发送的设置版本。 */
data class CompanionPresentationSnapshot(
    val snapshotVersion: Int = 1,
    val sourceSettingsRevision: Long = 0,
    val style: CompanionWritingStyle? = null,
    val pointOfView: CompanionPointOfView? = null,
    val extraStyle: String = "",
    val detailHint: CompanionDetailHint? = null,
    val observation: CompanionObservationSettings = CompanionObservationSettings(),
) : Serializable

enum class CompanionAttemptIntent { REGENERATE, RETRY }

object CompanionWritingValidation {
    const val MAX_CUSTOM_FIELDS = 6
    const val MAX_FIELD_LABEL = 40
    const val MAX_EXTRA_STYLE = 12_000
    const val MAX_TEMPLATES = 20
    const val MAX_TEMPLATE_NAME = 60

    fun error(settings: CompanionWritingSettings): String? {
        if (settings.extraStyle.length > MAX_EXTRA_STYLE) return "文风补充最多 $MAX_EXTRA_STYLE 字"
        if (settings.templates.size > MAX_TEMPLATES) return "最多保存 $MAX_TEMPLATES 个文风模板"
        if (settings.templates.any { it.id.isBlank() || it.id.length > 80 }) return "文风模板标识无效"
        if (settings.templates.map { it.id }
                .distinct().size != settings.templates.size) return "文风模板标识重复"
        if (settings.templates.any { it.name.isBlank() || it.name.trim().length > MAX_TEMPLATE_NAME })
            return "请为模板填写不超过 $MAX_TEMPLATE_NAME 字的名称"
        if (settings.templates.any { it.text.length > MAX_EXTRA_STYLE }) return "模板正文最多 $MAX_EXTRA_STYLE 字"
        if (duplicateLabels(settings.templates.map { it.name })) return "文风模板名称不能重复"
        return observationError(settings.observation)
    }

    fun observationError(observation: CompanionObservationSettings): String? {
        val builtinIds = companionBuiltinObservationFields().map { it.id }
        if (observation.fields.map { it.id }
                .toSet() != builtinIds.toSet() || observation.fields.size != 4)
            return "内建观测字段无效，请读取最新设置"
        if (observation.customFields.size > MAX_CUSTOM_FIELDS) return "自定义观测最多 $MAX_CUSTOM_FIELDS 项"
        if (observation.customFields.any { !Regex("obs_[A-Za-z0-9_-]{1,64}").matches(it.id) })
            return "自定义观测标识无效"
        val fields = observation.fields + observation.customFields
        if (fields.map { it.id }.distinct().size != fields.size) return "观测字段标识不能重复"
        if (fields.any { it.label.isBlank() || it.label.trim().length > MAX_FIELD_LABEL })
            return "请为观测项填写不超过 $MAX_FIELD_LABEL 字的名称"
        if (duplicateLabels(fields.map { it.label })) return "观测名称不能重复"
        return null
    }

    private fun duplicateLabels(labels: List<String>): Boolean =
        labels.map { it.trim().lowercase(Locale.ROOT) }.let { it.distinct().size != it.size }
}

/** 视觉键与声音标签独立；D4 素材及生成协议共用此键规则。 */
object CompanionExpressionKeys {
    private val pattern = Regex("[a-z][a-z0-9_-]{0,47}")
    private val reserved =
        setOf("observe", "field", "narration", "thought", "dialogue", "voice_emotion")

    fun normalize(value: String?): String? = value?.trim()?.lowercase(Locale.ROOT)
        ?.takeIf { pattern.matches(it) && it !in reserved }
}

enum class CompanionVoiceEmotion(val protocolValue: String) {
    HAPPY("happy"), SAD("sad"), ANGRY("angry"), FEARFUL("fearful"),
    DISGUSTED("disgusted"), SURPRISED("surprised"), CALM("calm");

    companion object {
        fun fromProtocol(value: String?): CompanionVoiceEmotion? =
            entries.firstOrNull { it.protocolValue == value?.trim()?.lowercase(Locale.ROOT) }
    }
}
