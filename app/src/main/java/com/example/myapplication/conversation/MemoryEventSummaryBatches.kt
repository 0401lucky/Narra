package com.example.myapplication.conversation

import com.example.myapplication.model.MemoryEventSummaryArtifact
import com.example.myapplication.model.MemoryEventSummaryMode
import com.example.myapplication.model.MemoryEventSummaryOutput
import com.example.myapplication.model.MemoryEventSummarySnapshot
import com.example.myapplication.model.MemoryEventSummaryWork
import com.example.myapplication.model.MemoryRecordVersion
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.system.json.AppJson

/** 整个成员及其来源不截断；软预算分批，全部批次完成才形成可发布覆盖。 */
internal object MemoryEventSummaryBatches {
    fun plan(snapshot: MemoryEventSummarySnapshot, targetChars: Int): MemoryEventSummaryWork {
        require(targetChars > 0) { "事件整理预算无效" }
        val ends = mutableListOf<Int>()
        var size = 0L
        snapshot.members.forEachIndexed { index, member ->
            val memberSize =
                member.record.content.length.toLong() + member.sourceRefs.sumOf { (it.end - it.start).toLong() } + 800L
            if (size > 0 && size + memberSize > targetChars) {
                ends += index
                size = 0
            }
            size += memberSize
        }
        ends += snapshot.members.size
        val work = MemoryEventSummaryWork(snapshot = snapshot, batchEnds = ends)
        // 在第一批网络前检查所有原料，避免最后一条极端大记录导致前面无意义收费。
        val previous = mutableListOf<MemoryEventSummaryOutput>()
        ends.indices.forEach { index ->
            val batch = batch(work, index, previous)
            MemoryEventSummaryFormatter.format(batch)
            previous += MemoryEventSummaryOutput(
                "原".repeat(MemoryEventSummaryFormatter.MAX_SUMMARY_CHARACTERS),
                "料".repeat(MemoryEventSummaryFormatter.MAX_SUMMARY_CHARACTERS),
                batch.members.map { MemoryRecordVersion(it.record.id, it.record.revision) },
            )
        }
        return work
    }

    fun readWork(json: String): MemoryEventSummaryWork {
        val work = AppJson.gson.fromJson(json, MemoryEventSummaryWork::class.java)
        require(work.version == 1 && work.snapshot.members.isNotEmpty()) { "事件整理快照无效" }
        require(
            work.batchEnds.isNotEmpty() && work.batchEnds.last() == work.snapshot.members.size &&
                    work.batchEnds.zipWithNext()
                        .all { (a, b) -> a < b } && work.batchEnds.first() > 0) { "事件整理分批范围无效" }
        require(work.snapshot.members.map { it.record.id }
            .distinct().size == work.snapshot.members.size) { "事件整理成员重复" }
        return work
    }

    fun readArtifact(json: String): MemoryEventSummaryArtifact =
        if (json.isBlank()) MemoryEventSummaryArtifact() else
            AppJson.gson.fromJson(json, MemoryEventSummaryArtifact::class.java).also {
                require(it.version == 1 && (it.pendingBatch == null || it.pendingBatch == it.completions.size)) { "事件整理进度无效" }
            }

    fun outputs(
        work: MemoryEventSummaryWork,
        artifact: MemoryEventSummaryArtifact
    ): List<MemoryEventSummaryOutput> {
        require(artifact.completions.size <= work.batchEnds.size) { "事件整理进度越界" }
        val outputs = mutableListOf<MemoryEventSummaryOutput>()
        artifact.completions.forEachIndexed { index, completion ->
            // UNKNOWN 可以留作审查材料，但不能被下一批的 COMPLETED 洗成可靠覆盖。
            require(completion.status == PromptCompletionStatus.COMPLETED) { "事件整理尚无完整结束证明" }
            outputs += MemoryEventSummaryParser.parse(completion, batch(work, index, outputs))
        }
        return outputs
    }

    fun batch(
        work: MemoryEventSummaryWork,
        index: Int,
        previous: List<MemoryEventSummaryOutput>
    ): MemoryEventSummarySnapshot {
        require(index in work.batchEnds.indices && previous.size == index) { "事件整理批次不连续" }
        val start = if (index == 0) 0 else work.batchEnds[index - 1]
        val base = work.snapshot
        return base.copy(
            mode = if (index == 0) base.mode else MemoryEventSummaryMode.INCREMENTAL,
            previousSummary = previous.lastOrNull()?.summary ?: base.previousSummary,
            previousState = previous.lastOrNull()?.currentState ?: base.previousState,
            previousCoverage = base.previousCoverage + previous.flatMap { it.coveredRecords },
            members = base.members.subList(start, work.batchEnds[index]),
            previousSummaryFromCurrentWork = index > 0,
        )
    }
}
