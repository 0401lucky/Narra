package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.*

object CompanionPresentationSupport {
    const val PROMPT_VERSION = "companion-presentation-v1"

    private val hints = listOf(
        "从已经出现的物件或习惯中挑一个具体线索；没有证据时不要新增共同经历。",
        "可以用角色的一次停顿或未说完的短句表达当下；不替用户解释感受。",
        "若已有环境信息，选择一个与此刻有关的感官细节，不堆叠固定镜头。",
        "留意角色资料中已有的措辞与习惯，让同一写法下的人物仍然不同。",
        "允许角色和用户共享沉默，细节无需承担推进关系的任务。",
        "承接上一轮已经写出的动作或位置，不为变化重新摆放人物。",
        "若角色注意到变化，只写有依据的可见线索，保留不确定与询问的空间。",
        "需要回应纠正或拒绝时，先按原意调整角色行为，不用文风掩盖边界。",
    )

    fun freeze(settings: CompanionCharacterSettings, seed: String): CompanionPresentationSnapshot {
        require(settings.schemaVersion == 1) { "此角色的陪伴设置版本较新，暂不能用于新请求" }
        require(CompanionWritingValidation.error(settings.writing) == null) {
            CompanionWritingValidation.error(settings.writing).orEmpty()
        }
        val writing = settings.writing
        val index = (companionRawHash(seed).take(8).toLong(16) % hints.size).toInt()
        return CompanionPresentationSnapshot(
            sourceSettingsRevision = settings.revision,
            style = writing.style,
            pointOfView = writing.pointOfView,
            extraStyle = writing.extraStyle,
            detailHint = if (writing.detailHintsEnabled) CompanionDetailHint(
                "detail-${index + 1}",
                hints[index]
            ) else null,
            observation = writing.observation.copy(
                fields = writing.observation.fields.map { it.copy() },
                customFields = writing.observation.customFields.map { it.copy() },
            ),
        )
    }

    fun prompt(snapshot: CompanionPresentationSnapshot, kind: CompanionTurnKind): String {
        require(snapshot.snapshotVersion == 1) { "本轮表现快照版本不受支持，请重新生成" }
        return buildList {
            add("【本轮陪伴表现】\n以下只控制叙述写法。角色资料、授权事实与用户执笔权保持优先；文风不改人物性格、权限或关系。")
            snapshot.style?.let { add("文风：${it.label}。${styleInstruction(it)}") }
            if (kind == CompanionTurnKind.OPENING) {
                add("开场候选保持第三人称，不套用后续会话的人称选择。")
            } else snapshot.pointOfView?.let { add(pointOfViewInstruction(it)) }
            if (snapshot.extraStyle.isNotBlank()) {
                add("自定义文风补充（在写法上优先于文风预设，仍受事实与执笔边界约束）：\n${snapshot.extraStyle}")
            }
            snapshot.detailHint?.let { add("本轮可选细节线索：${it.text}") }
            add("正文沿用 narration / thought / dialogue XML。可选 sprite 使用小写字母开头的英文键，后续仅限字母、数字、下划线与短横线；例如 normal、happy。可选 voice_emotion 仅接受 happy/sad/angry/fearful/disgusted/surprised/calm。视觉表情、自由文本 emotion 与声音情绪分别表达，不互相推断。")
            val observation = snapshot.observation
            if (observation.enabled && observation.enabledFields().isNotEmpty()) {
                add(buildString {
                    append("【同次回复观测】\n可在正文前输出一次 <observe version=\"1\">，用 <field id=\"稳定标识\">值</field> 后以 </observe> 结束。")
                    append("仅使用下列已启用标识；值是本次正文中有依据的角色或现场状态，无依据可留空。不要把用户未表达的内心当事实，也不要用观测添加经历。")
                    append(if (observation.style == CompanionObservationStyle.CONCISE) "每项简短。" else "每项可补具体依据，仍保持简洁。")
                    observation.enabledFields().forEach { field ->
                        append("\n- ").append(field.id).append("：").append(xmlText(field.label))
                    }
                    append("\n观测属于本条回复，不能复制上一轮未发生的状态；XML 文本中 &、<、> 必须转义。正文依旧是完整的 narration/thought/dialogue。")
                })
            } else add("本轮观测关闭，不输出 observe 块。")
        }.joinToString("\n\n")
    }

    private fun styleInstruction(style: CompanionWritingStyle): String = when (style) {
        CompanionWritingStyle.CINEMATIC -> "用可见的动作、空间与节奏呈现现场，避免镜头术语和强行转场。"
        CompanionWritingStyle.PLAIN -> "用准确、简洁的白描，减少修辞；需要时可以短句或沉默。"
        CompanionWritingStyle.LITERARY -> "用细腻具体的语言捕捉角色的感受，避免空泛抒情或代写用户内心。"
        CompanionWritingStyle.LIGHT_HUMOR -> "让轻快与幽默来自角色自己的习惯，不拿拒绝或脆弱开玩笑。"
        CompanionWritingStyle.INTENSE -> "让浓烈表达有角色和情境依据，不制造关系升级、身体反应或新的同意。"
    }

    private fun pointOfViewInstruction(pointOfView: CompanionPointOfView): String =
        when (pointOfView) {
            CompanionPointOfView.THIRD_NAME -> "叙述人称：第三人称，双方使用各自名字；台词中的我/你按正常说话保留。"
            CompanionPointOfView.THIRD_YOU -> "叙述人称：角色使用名字，用户称你；台词中的我/你按正常说话保留。"
            CompanionPointOfView.FIRST_YOU -> "叙述人称：角色以我叙述，用户称你；台词中的我/你按正常说话保留。"
        }

    private fun xmlText(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")
}
