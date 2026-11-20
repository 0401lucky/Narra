package com.example.myapplication.testutil

import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.*
import com.example.myapplication.viewmodel.MemoryArchiveTaskActions
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

/** 不构造 AppGraph 或 API；未声明的读取和全部意外写入立即失败。 */
class MemoryArchiveUiTestFixture {
    val spaces = MutableStateFlow(listOf(MemorySpace("daily", "a", MemoryPurpose.CONTINUOUS)))
    val records = mutableListOf(archiveUiRecord())
    var personas = listOf(
        MemoryArchivePersona("p", "当时身份"),
        MemoryArchivePersona("knowledge-only", "只有认知的身份")
    )
    val messages = mutableListOf<MemoryArchiveMessage>()
    val receipts = mutableMapOf<String, MemoryRecallReceipt>()
    val queries = mutableListOf<Pair<MemoryRuntimeContext, MemoryArchiveQuery>>()
    val receiptCalls = mutableListOf<List<String>>()
    val messageOffsets = mutableListOf<Int>()
    val writeCalls = mutableListOf<String>()
    var lastEdit: Triple<String, Long, String>? = null
    var editResult: MemoryWriteResult = MemoryWriteResult.Committed(listOf("r"))
    var editFailure: Throwable? = null
    var query: suspend (MemoryRuntimeContext, MemoryArchiveQuery) -> MemoryArchivePage<MemoryArchiveItem> =
        { context, request ->
            val values =
                records.filter { it.spaceId == context.spaceId && it.personaKey == context.personaKey }
                    .filter { request.category == null || it.category == request.category }
                    .filter { it.content.contains(request.search) }
                    .map { MemoryArchiveItem.Record(it) }
            MemoryArchivePage(
                values.drop(request.offset).take(request.limit),
                values.size,
                request.offset,
                request.limit
            )
        }
    var revisionSources = emptyMap<Long, List<MemorySourceEvidence>>()
    var jobs = emptyList<MemoryProcessingJob>()
    var resumeCalls = mutableListOf<String>()

    val repository: MemoryArchiveRepository = Proxy.newProxyInstance(
        MemoryArchiveRepository::class.java.classLoader,
        arrayOf(MemoryArchiveRepository::class.java),
    ) { proxy, method, arguments ->
        val args = arguments.orEmpty()
        when (method.name) {
            "toString" -> "ExplicitMemoryArchiveUiFake"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args[0]
            "observeSpaces" -> spaces
            else -> {
                @Suppress("UNCHECKED_CAST")
                val continuation = args.last() as Continuation<Any?>
                val body: suspend () -> Any? = { invoke(method.name, args) }
                body.startCoroutineUninterceptedOrReturn(continuation)
            }
        }
    } as MemoryArchiveRepository

    private suspend fun invoke(name: String, args: Array<out Any?>): Any? = when (name) {
        "getManagementContext" -> spaces.value.find { it.id == args[1] && it.characterId == args[0] }
            ?.let {
                MemoryRuntimeContext(
                    it.characterId,
                    it.id,
                    "",
                    it.scenarioId,
                    it.runId,
                    args[2] as String,
                    it.generation
                )
            }

        "getRuntimeContext" -> null
        "getSpace" -> spaces.value.find { it.id == args[0] }
        "listSpaces" -> spaces.value
        "listArchiveCharacterIds" -> spaces.value.map { it.characterId }.distinct()
        "countArchiveItems" -> records.count { it.characterId == args[0] }
        "listArchivePersonas" -> personas
        "listArchiveConversations" -> listOf(
            MemoryArchiveConversation(
                "conv",
                "合成旧对话",
                "scene",
                "run",
                true,
                true,
                messages.size,
                personas.map { it.key })
        )

        "listArchiveMessages" -> {
            val context = args[0] as MemoryRuntimeContext
            val limit = args[2] as Int
            val offset = args[3] as Int
            messageOffsets += offset
            val rows = messages.filter { it.personaKey == context.personaKey }
                .sortedWith(compareBy<MemoryArchiveMessage> { it.message.createdAt }.thenBy { it.message.id })
            MemoryArchivePage(rows.drop(offset).take(limit), rows.size, offset, limit)
        }

        "getLatestArchiveAssistantMessage" -> messages.filter { it.message.role == MessageRole.ASSISTANT }
            .maxWithOrNull(compareBy<MemoryArchiveMessage> { it.message.createdAt }.thenBy { it.message.id })

        "queryArchive" -> {
            val context = args[0] as MemoryRuntimeContext
            val request = args[1] as MemoryArchiveQuery
            queries += context to request
            query(context, request)
        }

        "listJobHistory" -> jobs.drop(args[2] as Int).take(args[1] as Int)
        "getProcessingControl" -> MemoryProcessingControl(
            (args[0] as MemoryRuntimeContext).spaceId,
            args[1] as String
        )

        "getRecordDetails" -> records.find { it.id == args[1] }?.let { record ->
            MemoryRecordDetails(
                record,
                (1..record.revision).map {
                    MemoryRecordRevision(
                        record.id,
                        it,
                        "版本 $it",
                        MemoryChangeOrigin.MANUAL,
                        it
                    )
                },
                emptyList(),
                emptyList()
            )
        }

        "getRecordRevisionDetails" -> records.find { it.id == args[1] }?.let { record ->
            val revision = args[2] as Long
            val sources = revisionSources[revision].orEmpty()
            MemoryRecordRevisionDetails(
                record,
                MemoryRecordRevision(
                    record.id,
                    revision,
                    if (revision == record.revision) record.content else "旧版本 $revision",
                    MemoryChangeOrigin.MANUAL,
                    revision
                ),
                sources,
                sources.map { it.fullRef() })
        }

        "getArchivedRecallReceipt" -> {
            val key = listOf(args[1] as String, args[2] as String, args[3] as String)
            receiptCalls += key
            receipts[key[2]]
        }

        "editRecord" -> {
            writeCalls += name
            lastEdit = Triple(args[1] as String, args[2] as Long, args[3] as String)
            editFailure?.let { throw it }
            editResult
        }

        "ensureDailySpace" -> {
            writeCalls += name
            val space = MemorySpace("daily", args[0] as String, MemoryPurpose.CONTINUOUS)
            spaces.value = listOf(space)
            MemoryRuntimeContext(space.characterId, space.id, "", personaKey = args[1] as String)
        }

        "getSource" -> revisionSources.values.flatten().find { it.id == args[1] }
        else -> error("档案 UI 测试不应调用 $name")
    }
}

fun archiveUiRecord(
    id: String = "r",
    spaceId: String = "daily",
    content: String = "第一段\n\n第二段",
    revision: Long = 3
) =
    MemoryRecord(
        id,
        spaceId,
        "a",
        MemoryRecordKind.EPISODE,
        MemoryCategory.DAILY,
        content,
        revision = revision,
        personaKey = "p"
    )

fun rejectingArchiveTasks() = MemoryArchiveTaskActions(
    processSelection = { _, _, _, _ -> error("读取测试不得请求模型") },
    resumeExtraction = { _, _ -> error("读取测试不得继续模型任务") },
    resumeEvent = { _, _ -> error("读取测试不得继续事件任务") },
    rebuildEvent = { _, _, _, _ -> error("读取测试不得重新请求模型") },
)
