package com.example.myapplication.context

import com.example.myapplication.model.MemoryRecallOmission
import com.example.myapplication.model.MemoryRecallOmissionReason
import com.example.myapplication.model.MemoryRecallPlan
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.ContextLogSection
import java.util.UUID

data class MemoryRecallAssembly(
    val systemPrompt: String,
    val envelope: PromptEnvelope,
    val plan: MemoryRecallPlan
)

object MemoryRecallAssemblySupport {
    fun renderPreset(
        renderer: PresetPromptRenderer,
        input: PresetPromptRenderInput,
        plan: MemoryRecallPlan
    ): RenderedPresetPrompt {
        if (plan.section.isBlank()) return renderer.render(input)
        val literalToken = "narra_memory_literal_${UUID.randomUUID()}"
        // 模板只展开包装层；来源正文中的占位符、标签与用户字面内容保持原样。
        val protected = input.copy(slotValues = input.slotValues.mapValues { (_, value) ->
            value.replace(plan.section, literalToken)
        })
        val rendered = renderer.render(protected)
        fun restore(text: String) = text.replace(literalToken, plan.section)
        return rendered.copy(
            systemPrompt = restore(rendered.systemPrompt),
            promptEnvelope = rendered.promptEnvelope.mapContent(::restore),
            contextSections = rendered.contextSections.map { section ->
                val content = restore(section.content)
                if (content == section.content) section else ContextLogSection(
                    section.sourceType,
                    section.title,
                    content
                )
            },
        )
    }

    fun reconcile(
        systemPrompt: String,
        envelope: PromptEnvelope,
        plan: MemoryRecallPlan
    ): MemoryRecallAssembly {
        val found = mutableSetOf<String>()
        val duplicated = mutableSetOf<String>()
        var sectionFound = false
        fun rewrite(text: String): String {
            var result = text
            if (plan.section.isNotBlank()) {
                var from = 0
                while (true) {
                    val position = result.indexOf(plan.section, from)
                    if (position < 0) break
                    if (!sectionFound) {
                        sectionFound = true
                        from = position + plan.section.length
                    } else {
                        result = result.removeRange(position, position + plan.section.length)
                        duplicated += plan.materials.map { it.ref.kind.name + ":" + it.ref.id }
                        from = position
                    }
                }
            }
            plan.materials.forEach { material ->
                val key = material.ref.kind.name + ":" + material.ref.id
                val block = MemoryRecallRendering.block(material)
                var from = 0
                while (true) {
                    val position = result.indexOf(block, from)
                    if (position < 0) break
                    if (found.add(key)) from = position + block.length
                    else {
                        result = result.removeRange(position, position + block.length)
                        duplicated += key
                        from = position
                    }
                }
            }
            val unmatched = plan.materials.fold(result) { textWithoutBlocks, material ->
                textWithoutBlocks.replace(MemoryRecallRendering.block(material), "")
            }
            check(plan.materials.none { "<narra_memory key=\"${it.renderKey}\"" in unmatched }) {
                "本轮记忆正文已改变，请刷新后重试"
            }
            return result
        }

        val prompt = rewrite(systemPrompt)
        val updatedEnvelope = envelope.mapContent(::rewrite)
        val materials = plan.materials.filter { it.ref.kind.name + ":" + it.ref.id in found }
        val omissions = plan.materials.mapNotNull { material ->
            val key = material.ref.kind.name + ":" + material.ref.id
            when {
                key !in found -> MemoryRecallOmission(
                    material.ref,
                    MemoryRecallOmissionReason.NOT_RENDERED
                )

                key in duplicated -> MemoryRecallOmission(
                    material.ref,
                    MemoryRecallOmissionReason.DUPLICATE_RENDERING
                )

                else -> null
            }
        }
        val section = MemoryRecallRendering.section(materials)
        return MemoryRecallAssembly(
            prompt, updatedEnvelope, plan.copy(
                materials = materials,
                section = section,
                estimatedTokens = MemoryRecallText.estimateTokens(section),
                omitted = (plan.omitted + omissions).distinct()
            )
        )
    }

    private fun PromptEnvelope.mapContent(transform: (String) -> String) = copy(
        preHistoryMessages = preHistoryMessages.map { it.copy(content = transform(it.content)) },
        historyInjections = historyInjections.map { it.copy(content = transform(it.content)) },
        postHistoryMessages = postHistoryMessages.map { it.copy(content = transform(it.content)) },
    )
}
