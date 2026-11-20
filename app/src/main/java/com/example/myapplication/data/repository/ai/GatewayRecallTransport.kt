package com.example.myapplication.data.repository.ai

import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.context.MemoryRecallRendering
import com.example.myapplication.context.MemoryRecallText
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.MemoryRecallOmission
import com.example.myapplication.model.MemoryRecallOmissionReason
import com.example.myapplication.model.MemoryRecallRequest
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Response
import okio.Buffer
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** 本轮上下文随协程传到真正的协议调用点，不能由共享的“最新日志”推断。 */
internal class GatewayRecallSession(
    private val request: MemoryRecallRequest,
    private val archive: MemoryArchiveRepository,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<GatewayRecallSession>

    private var attempted = false
    private var confirmed = false
    private var httpFailure = false
    private var terminalFailureCode: String? = null
    private var applicationTextPaths: Set<String>? = null

    private data class PayloadText(val path: String, val value: String)

    suspend fun prepare() {
        check(archive.prepareRecallReceipt(request)) { "本轮记忆现场已失效，请重新发送" }
    }

    suspend fun payload(rawJson: String): Int {
        if (!archive.validateRecallMaterials(
                request.plan.context,
                request.plan.materials.map { it.ref })
        ) {
            terminalFailureCode = "SOURCE_CHANGED"
            error("本轮记忆来源或运行已改变，请重新发送")
        }
        val root = JsonParser.parseString(rawJson)
        val allTexts = mutableListOf<PayloadText>()
        fun collect(value: JsonElement, path: String) {
            when {
                value.isJsonPrimitive && value.asJsonPrimitive.isString ->
                    allTexts += PayloadText(path, value.asString.replace("\r\n", "\n"))

                value.isJsonArray -> value.asJsonArray.forEachIndexed { index, child ->
                    collect(
                        child,
                        "$path/$index"
                    )
                }

                value.isJsonObject -> value.asJsonObject.entrySet().forEach { (name, child) ->
                    if (name in TEXT_FIELDS) collect(child, "$path/$name")
                }
            }
        }
        if (root.isJsonObject) root.asJsonObject.entrySet().forEach { (name, value) ->
            if (name in setOf("messages", "input", "system", "instructions")) collect(value, name)
        }
        // 工具循环只追加模型/工具输出；后续引用当前 key 也不能变成应用注入的载体。
        val texts = allTexts.filter { applicationTextPaths?.contains(it.path) != false }
        val matchedPaths = mutableSetOf<String>()
        val matched = mutableListOf<com.example.myapplication.model.MemoryRecallMaterial>()
        val omitted = mutableListOf<MemoryRecallOmission>()
        val unmatched = texts.map { it.value }.toMutableList()
        request.plan.materials.forEach { material ->
            val block = MemoryRecallRendering.block(material).replace("\r\n", "\n")
            var occurrences = 0
            unmatched.indices.forEach { index ->
                var offset = unmatched[index].indexOf(block)
                while (offset >= 0) {
                    occurrences++
                    matchedPaths += texts[index].path
                    unmatched[index] = unmatched[index].removeRange(offset, offset + block.length)
                    offset = unmatched[index].indexOf(block, offset)
                }
            }
            if (occurrences > 1) {
                terminalFailureCode = "PAYLOAD_CHANGED"
                error("本轮记忆重复注入，请刷新后重试")
            }
            if (occurrences == 1) matched += material
            else omitted += MemoryRecallOmission(
                material.ref,
                MemoryRecallOmissionReason.NOT_RENDERED
            )
        }
        val controlledKeys = request.plan.materials.map { "<narra_memory key=\"${it.renderKey}\"" }
        if (unmatched.any { text -> controlledKeys.any { it in text } } ||
            MemoryRecallText.estimateTokens(MemoryRecallRendering.section(matched)) > request.plan.tokenBudget) {
            terminalFailureCode = "PAYLOAD_CHANGED"
            error("本轮记忆正文或预算已改变，请刷新后重试")
        }
        val sequence = archive.prepareRecallTransmission(
            request.requestId,
            MemoryArchiveIdentity.fingerprint(rawJson),
            matched.map { it.ref },
            omitted
        )
        if (sequence == null) {
            terminalFailureCode = "SOURCE_CHANGED"
            error("本轮记忆来源或运行已改变，请重新发送")
        }
        if (applicationTextPaths == null) applicationTextPaths = matchedPaths.toSet()
        attempted = false
        confirmed = false
        httpFailure = false
        return sequence
    }

    fun sending() {
        attempted = true
    }

    fun rejectedBeforeSend() {
        terminalFailureCode = "SOURCE_CHANGED"
    }

    suspend fun received(sequence: Int, successful: Boolean) = withContext(NonCancellable) {
        check(
            archive.confirmRecallTransmission(
                request.requestId,
                sequence
            )
        ) { "记忆发送回执保存失败" }
        confirmed = true
        httpFailure = !successful
    }

    suspend fun failed(cancellation: Boolean) = withContext(NonCancellable) {
        val code = terminalFailureCode ?: when {
            cancellation && confirmed -> "CANCELLED_AFTER_SEND"
            cancellation && attempted -> "CANCELLED_UNCONFIRMED"
            !attempted -> "NOT_STARTED"
            httpFailure -> "HTTP_FAILED"
            !confirmed -> "TRANSPORT_UNCONFIRMED"
            else -> "REQUEST_FAILED"
        }
        archive.failRecallRequest(request.requestId, code)
    }
}

internal object GatewayRecallTransport {
    data class BoundPrompt(
        val systemPrompt: String,
        val envelope: com.example.myapplication.model.PromptEnvelope
    )

    fun bind(
        systemPrompt: String,
        envelope: com.example.myapplication.model.PromptEnvelope
    ): BoundPrompt {
        val request = envelope.memoryRecallRequest ?: return BoundPrompt(systemPrompt, envelope)
        val materials = request.plan.materials.map { material ->
            material.copy(
                renderKey = MemoryArchiveIdentity.id(
                    "recall-render",
                    request.requestId,
                    material.renderKey
                )
            )
        }
        val replacements = request.plan.materials.zip(materials).map { (before, after) ->
            MemoryRecallRendering.block(before) to MemoryRecallRendering.block(after)
        }

        fun rewrite(text: String): String {
            val blocks = replacements.fold(text) { current, (before, after) ->
                current.replace(
                    before,
                    after
                )
            }
            return request.plan.materials.zip(materials).fold(blocks) { current, (before, after) ->
                current.replace(
                    "<narra_memory key=\"${before.renderKey}\"",
                    "<narra_memory key=\"${after.renderKey}\""
                )
            }
        }

        val plan = request.plan.copy(
            materials = materials,
            section = MemoryRecallRendering.section(materials)
        )
        // 只重写应用掌握的插入位置。用户/助手历史中的旧块不取得本轮标记。
        return BoundPrompt(
            rewrite(systemPrompt), envelope.copy(
                preHistoryMessages = envelope.preHistoryMessages.map { it.copy(content = rewrite(it.content)) },
                historyInjections = envelope.historyInjections.map { it.copy(content = rewrite(it.content)) },
                postHistoryMessages = envelope.postHistoryMessages.map {
                    it.copy(
                        content = rewrite(
                            it.content
                        )
                    )
                },
                memoryRecallRequest = request.copy(plan = plan),
            )
        )
    }

    fun openAi(delegate: com.example.myapplication.data.remote.OpenAiCompatibleApi): com.example.myapplication.data.remote.OpenAiCompatibleApi =
        object : com.example.myapplication.data.remote.OpenAiCompatibleApi by delegate {
            override suspend fun createChatCompletion(request: com.example.myapplication.model.ChatCompletionRequest) =
                retrofit(request) { delegate.createChatCompletion(request) }

            override suspend fun createChatCompletionAt(
                url: String,
                request: com.example.myapplication.model.ChatCompletionRequest
            ) =
                retrofit(request) { delegate.createChatCompletionAt(url, request) }

            override suspend fun createResponseAt(
                url: String,
                request: com.example.myapplication.model.ResponseApiRequest
            ) =
                retrofit(request) { delegate.createResponseAt(url, request) }
        }

    fun anthropic(delegate: com.example.myapplication.data.remote.AnthropicApi): com.example.myapplication.data.remote.AnthropicApi =
        object : com.example.myapplication.data.remote.AnthropicApi by delegate {
            override suspend fun createMessage(request: com.example.myapplication.model.AnthropicMessageRequest) =
                retrofit(request) { delegate.createMessage(request) }
        }

    suspend fun execute(call: Call): Response {
        currentCoroutineContext()[GatewayCompanionSession]?.validate()
        val session = currentCoroutineContext()[GatewayRecallSession]
        val sequence =
            session?.payload(Buffer().also { call.request().body?.writeTo(it) }.readUtf8())
        currentCoroutineContext()[GatewayCompanionSession]?.validate()
        currentCoroutineContext().ensureActive()
        session?.sending()
        val response = call.execute()
        try {
            if (sequence != null) session.received(sequence, response.isSuccessful)
            return response
        } catch (throwable: Throwable) {
            response.close()
            throw throwable
        }
    }

    suspend fun <T> retrofit(
        payload: Any,
        request: suspend () -> retrofit2.Response<T>
    ): retrofit2.Response<T> {
        currentCoroutineContext()[GatewayCompanionSession]?.validate()
        val session = currentCoroutineContext()[GatewayRecallSession]
        val sequence = session?.payload(AppJson.gson.toJson(payload))
        currentCoroutineContext()[GatewayCompanionSession]?.validate()
        currentCoroutineContext().ensureActive()
        session?.sending()
        val response = request()
        if (sequence != null) session.received(sequence, response.isSuccessful)
        return response
    }
}

private val TEXT_FIELDS = setOf("content", "text", "input", "messages", "system", "instructions")
