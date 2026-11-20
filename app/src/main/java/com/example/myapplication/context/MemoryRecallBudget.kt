package com.example.myapplication.context

import com.example.myapplication.model.MemoryMaterialKind
import com.example.myapplication.model.MemoryMaterialRef
import com.example.myapplication.model.MemoryRecallLayer
import com.example.myapplication.model.MemoryRecallMaterial
import com.example.myapplication.model.MemoryRecallOmission
import com.example.myapplication.model.MemoryRecallOmissionReason
import com.example.myapplication.model.MemoryRecallPlan
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemoryVectorAvailability

data class MemoryRecallCandidate(
    val material: MemoryRecallMaterial,
    val priority: Double,
    val hitStart: Int = 0,
    val hitEnd: Int = material.content.length,
    val requiredStart: Int? = null,
    val requiredEnd: Int? = null,
)

object MemoryRecallRendering {
    const val HEADER =
        "【本轮记忆材料】\n来源可信度以逐条标签为准；经历、主观看法、旧未验证材料与当前状态分别使用。片段只代表标注范围。认知仅作背景，无须复述或催办；明确角色设定与世界书优先，主观看法不当作事实。"

    fun block(material: MemoryRecallMaterial): String = buildString {
        val ref = material.ref
        append("<narra_memory key=\"").append(material.renderKey).append("\" kind=\"")
            .append(ref.kind.name).append("\" id=\"").append(attribute(ref.id))
        append("\" revision=\"").append(ref.revision).append("\" span=\"").append(ref.spanStart)
            .append(':').append(ref.spanEnd)
        append("\" fragment=\"").append(material.fragment).append("\">\n")
        append(material.label).append('\n').append(material.content).append("\n</narra_memory>")
    }

    fun section(materials: List<MemoryRecallMaterial>): String = if (materials.isEmpty()) "" else
        HEADER + "\n" + materials.joinToString("\n", transform = ::block)

    private fun attribute(value: String) =
        value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
            .replace(">", "&gt;")
}

/** 最终字符串含标签一起计费；借用空闲额度不会改动任何存档正文。 */
class MemoryRecallBudget {
    fun allocate(
        context: MemoryRuntimeContext,
        candidates: List<MemoryRecallCandidate>,
        maxExperiences: Int,
        tokenBudget: Int,
        vectorAvailability: MemoryVectorAvailability = MemoryVectorAvailability.NOT_REQUESTED,
    ): MemoryRecallPlan {
        val budget = tokenBudget.coerceAtLeast(1)
        val unique =
            candidates.groupBy { it.material.ref.kind to it.material.ref.id }.values.map { group ->
                val first = group.first()
                val hit = group.filter { it.material.layer == MemoryRecallLayer.EXPERIENCE }
                    .maxByOrNull { it.priority }
                // 置顶保留独立额度和条数规则，但不能丢掉同一记录已经定位的相关片段。
                if (first.material.layer == MemoryRecallLayer.PINNED && hit != null) {
                    first.copy(
                        priority = maxOf(first.priority, hit.priority),
                        hitStart = hit.hitStart,
                        hitEnd = hit.hitEnd,
                        requiredStart = hit.requiredStart,
                        requiredEnd = hit.requiredEnd
                    )
                } else first
            }
        val omissions = mutableListOf<MemoryRecallOmission>()
        var experiences = 0
        val eligible = unique.sortedByDescending { it.priority }.filter { candidate ->
            if (candidate.material.layer != MemoryRecallLayer.EXPERIENCE || candidate.material.ref.kind != MemoryMaterialKind.RECORD) true
            else if (experiences++ < maxExperiences.coerceAtLeast(0)) true
            else {
                omissions += MemoryRecallOmission(
                    candidate.material.ref,
                    MemoryRecallOmissionReason.ITEM_LIMIT
                ); false
            }
        }
        val selected = linkedMapOf<Pair<MemoryMaterialKind, String>, MemoryRecallMaterial>()
        val headerCost = MemoryRecallText.estimateTokens(MemoryRecallRendering.HEADER + "\n")
        var remaining = (budget - headerCost).coerceAtLeast(0)
        val shares = mapOf(
            MemoryRecallLayer.RESIDENT to 0.30,
            MemoryRecallLayer.PINNED to 0.10,
            MemoryRecallLayer.EXPERIENCE to 0.50,
            MemoryRecallLayer.STATE to 0.10
        )

        fun cost(material: MemoryRecallMaterial) =
            MemoryRecallText.estimateTokens(MemoryRecallRendering.block(material) + "\n")

        fun accept(candidate: MemoryRecallCandidate, allowance: Int): Int {
            val full = candidate.material
            val material =
                if (cost(full) <= allowance) full else fragment(candidate, allowance, ::cost)
                    ?: return 0
            selected[full.ref.kind to full.ref.id] = material
            return cost(material)
        }
        MemoryRecallLayer.entries.forEach { layer ->
            var allowance = minOf(
                remaining,
                ((budget - headerCost) * shares.getValue(layer)).toInt().coerceAtLeast(0)
            )
            eligible.filter { it.material.layer == layer }.sortedByDescending { it.priority }
                .forEach { candidate ->
                    val used = accept(candidate, allowance)
                    allowance -= used
                    remaining -= used
                }
        }
        // 未消耗的层额度优先归还明确命中的经历，再供其它完整条目使用。
        eligible.sortedByDescending { it.priority }.forEach { candidate ->
            val key = candidate.material.ref.kind to candidate.material.ref.id
            val previous = selected[key]
            if (previous == null) remaining -= accept(candidate, remaining)
            else if (previous.fragment && cost(candidate.material) <= remaining + cost(previous)) {
                remaining += cost(previous) - cost(candidate.material)
                selected[key] = candidate.material
            }
        }
        eligible.forEach { candidate ->
            val sent = selected[candidate.material.ref.kind to candidate.material.ref.id]
            if (sent == null) omissions += MemoryRecallOmission(
                candidate.material.ref,
                MemoryRecallOmissionReason.TOKEN_BUDGET
            )
            else if (sent.fragment) omissions += MemoryRecallOmission(
                candidate.material.ref,
                MemoryRecallOmissionReason.PARTIAL_RECORD
            )
        }
        val materials = selected.values.toMutableList()
        while (materials.isNotEmpty() && MemoryRecallText.estimateTokens(
                MemoryRecallRendering.section(
                    materials
                )
            ) > budget
        ) {
            val removed = materials.removeAt(materials.lastIndex)
            omissions += MemoryRecallOmission(removed.ref, MemoryRecallOmissionReason.TOKEN_BUDGET)
        }
        val section = MemoryRecallRendering.section(materials)
        return MemoryRecallPlan(
            context, unique.map { it.material.ref }, materials, omissions.distinct(), section,
            MemoryRecallText.estimateTokens(section), budget, vectorAvailability
        )
    }

    private fun fragment(
        candidate: MemoryRecallCandidate,
        allowance: Int,
        cost: (MemoryRecallMaterial) -> Int
    ): MemoryRecallMaterial? {
        val full = candidate.material
        val canFragment = full.layer == MemoryRecallLayer.EXPERIENCE ||
                (full.layer == MemoryRecallLayer.PINNED && candidate.requiredStart != null && candidate.requiredEnd != null)
        if (full.ref.kind != MemoryMaterialKind.RECORD || !canFragment || allowance <= 0) return null
        val text = full.content
        var start = MemoryRecallText.safeBoundary(text, candidate.hitStart)
        var end = MemoryRecallText.safeBoundary(text, candidate.hitEnd.coerceAtLeast(start + 1))
        if (end <= start) return null
        val requiredStart = candidate.requiredStart ?: start
        val requiredEnd = candidate.requiredEnd ?: end
        if (requiredStart < start || requiredEnd > end || requiredEnd <= requiredStart) return null
        // 命中契约没有最短窗口；短姓名/编号先补周边原文，额度不足时仍保留完整必要命中。
        val contextLength = minOf(24, text.length)
        if (end - start < contextLength) {
            val before = (contextLength - (end - start) + 1) / 2
            start = MemoryRecallText.safeBoundary(text, start - before)
            end = MemoryRecallText.safeBoundary(text, maxOf(end, start + contextLength))
            start = MemoryRecallText.safeBoundary(text, minOf(start, end - contextLength))
        }
        fun build(): MemoryRecallMaterial {
            val content = text.substring(start, end)
            return full.copy(
                content = content, fragment = true, ref = full.ref.copy(
                    spanStart = start, spanEnd = end,
                    contentFingerprint = MemoryArchiveIdentity.fingerprint(content)
                )
            )
        }

        var material = build()
        while (cost(material) > allowance) {
            val shortened = MemoryRecallText.safeBoundary(text, end - maxOf(8, (end - start) / 8))
            if (shortened >= requiredEnd && shortened < end) {
                end = shortened
            } else if (end > requiredEnd && MemoryRecallText.safeBoundary(
                    text,
                    requiredEnd
                ) == requiredEnd
            ) {
                end = requiredEnd
            } else {
                var advanced = MemoryRecallText.safeBoundary(
                    text,
                    minOf(requiredStart, start + maxOf(8, (end - start) / 8))
                )
                if (advanced <= start) {
                    advanced = start + Character.charCount(text.codePointAt(start))
                }
                if (advanced > requiredStart || advanced <= start) return null
                start = advanced
            }
            material = build()
        }
        return material
    }
}
