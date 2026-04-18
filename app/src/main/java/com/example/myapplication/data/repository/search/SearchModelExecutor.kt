package com.example.myapplication.data.repository.search

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.SearchSourceType
import com.example.myapplication.system.json.AppJson
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SearchModelExecutor(
    private val settingsStore: SettingsStore,
    private val apiServiceFactory: ApiServiceFactory,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private data class StructuredSearchPayload(
        val answer: String,
        val items: List<SearchResultItem>,
    )

    suspend fun search(
        source: com.example.myapplication.model.SearchSourceConfig,
        query: String,
        resultCount: Int,
    ): SearchResult = withContext(Dispatchers.IO) {
        val settings = settingsStore.settingsFlow.first()
        require(source.providerId.isNotBlank()) { "请先在搜索源里选择搜索提供商" }
        val provider = settings.resolveSearchSourceProvider(source)
            ?: throw IllegalStateException("当前搜索提供商不可用，请检查提供商启用状态与连接配置")
        val searchModel = provider.resolveFunctionModel(ProviderFunction.SEARCH)
        require(searchModel.isNotBlank()) { "请先在模型页开启搜索模型" }
        require(provider.hasBaseCredentials()) { "当前提供商缺少连接配置，无法执行 LLM 搜索" }

        when (provider.resolvedApiProtocol()) {
            ProviderApiProtocol.OPENAI_COMPATIBLE -> {
                require(provider.supportsLlmSearchSource()) {
                    "LLM 搜索要求当前提供商使用 Responses API"
                }
                searchWithResponses(
                    provider = provider,
                    model = searchModel,
                    query = query,
                    resultCount = resultCount,
                )
            }

            ProviderApiProtocol.ANTHROPIC -> {
                require(provider.supportsLlmSearchSource()) {
                    "LLM 搜索要求当前提供商使用 Anthropic 搜索工具"
                }
                searchWithAnthropic(
                    provider = provider,
                    model = searchModel,
                    query = query,
                    resultCount = resultCount,
                )
            }
        }
    }

    private fun searchWithResponses(
        provider: ProviderSettings,
        model: String,
        query: String,
        resultCount: Int,
    ): SearchResult {
        val requestBody = gson.toJson(
            mapOf(
                "model" to model,
                "input" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to buildExtractionPrompt(query, resultCount),
                    ),
                ),
                "tools" to listOf(
                    mapOf(
                        "type" to "web_search",
                    ),
                ),
            ),
        )
        val request = Request.Builder()
            .url(buildResponsesUrl(provider))
            .addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("LLM 搜索失败：${response.code}")
            }
            parseResponsesSearchResult(
                root = JsonParser.parseString(body).asJsonObject,
                query = query,
                resultCount = resultCount,
                sourceLabel = SearchSourceType.LLM_SEARCH.label,
            )
        }
    }

    private fun searchWithAnthropic(
        provider: ProviderSettings,
        model: String,
        query: String,
        resultCount: Int,
    ): SearchResult {
        val requestBody = gson.toJson(
            mapOf(
                "model" to model,
                "max_tokens" to 2048,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to buildExtractionPrompt(query, resultCount),
                    ),
                ),
                "tools" to listOf(
                    mapOf(
                        "type" to "web_search_20250305",
                        "name" to "web_search",
                        "max_uses" to 5,
                    ),
                ),
            ),
        )
        val request = Request.Builder()
            .url(buildAnthropicUrl(provider))
            .addHeader("x-api-key", provider.apiKey.trim())
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("LLM 搜索失败：${response.code}")
            }
            parseAnthropicSearchResult(
                root = JsonParser.parseString(body).asJsonObject,
                query = query,
                resultCount = resultCount,
                sourceLabel = SearchSourceType.LLM_SEARCH.label,
            )
        }
    }

    private fun parseResponsesSearchResult(
        root: JsonObject,
        query: String,
        resultCount: Int,
        sourceLabel: String,
    ): SearchResult {
        val citations = mutableListOf<SearchResultItem>()
        val textBuilder = StringBuilder()
        root.getAsJsonArray("output").orEmpty().forEach { item ->
            val output = item.asJsonObject
            if (output.getString("type") != "message") {
                return@forEach
            }
            output.getAsJsonArray("content").orEmpty().forEach { contentElement ->
                val content = contentElement.asJsonObject
                val contentType = content.getString("type")
                if (contentType == "output_text" || contentType == "text") {
                    textBuilder.append(content.getString("text"))
                    content.getAsJsonArray("annotations").orEmpty().forEach { annotationElement ->
                        val annotation = annotationElement.asJsonObject
                        val url = annotation.getString("url").trim()
                        if (url.isBlank()) {
                            return@forEach
                        }
                        citations += SearchResultItem(
                            id = stableSearchResultId(url),
                            title = annotation.getString("title").ifBlank { url },
                            url = url,
                            snippet = "",
                            sourceLabel = sourceLabel,
                        )
                    }
                }
            }
        }
        root.getAsJsonArray("citations").orEmpty().forEach { citationElement ->
            val citation = citationElement.asJsonObject
            val url = citation.getString("url").trim()
            if (url.isBlank()) {
                return@forEach
            }
            citations += SearchResultItem(
                id = stableSearchResultId(url),
                title = citation.getString("title").ifBlank { url },
                url = url,
                snippet = citation.getString("cited_text"),
                sourceLabel = sourceLabel,
            )
        }
        return mergeStructuredSearchResult(
            query = query,
            resultCount = resultCount,
            responseText = textBuilder.toString(),
            citations = citations,
            sourceLabel = sourceLabel,
        )
    }

    private fun parseAnthropicSearchResult(
        root: JsonObject,
        query: String,
        resultCount: Int,
        sourceLabel: String,
    ): SearchResult {
        val citations = mutableListOf<SearchResultItem>()
        val textBuilder = StringBuilder()
        root.getAsJsonArray("content").orEmpty().forEach { element ->
            val item = element.asJsonObject
            when (item.getString("type")) {
                "text" -> {
                    textBuilder.append(item.getString("text"))
                    item.getAsJsonArray("citations").orEmpty().forEach { citationElement ->
                        val citation = citationElement.asJsonObject
                        val url = citation.getString("url").trim()
                        if (url.isBlank()) {
                            return@forEach
                        }
                        citations += SearchResultItem(
                            id = stableSearchResultId(url),
                            title = citation.getString("title").ifBlank { url },
                            url = url,
                            snippet = citation.getString("cited_text"),
                            sourceLabel = sourceLabel,
                        )
                    }
                }

                "web_search_tool_result" -> {
                    item.getAsJsonArray("content").orEmpty().forEach { resultElement ->
                        val result = resultElement.asJsonObject
                        if (result.getString("type") != "web_search_result") {
                            return@forEach
                        }
                        val url = result.getString("url").trim()
                        if (url.isBlank()) {
                            return@forEach
                        }
                        citations += SearchResultItem(
                            id = stableSearchResultId(url),
                            title = result.getString("title").ifBlank { url },
                            url = url,
                            snippet = "",
                            sourceLabel = sourceLabel,
                        )
                    }
                }
            }
        }
        return mergeStructuredSearchResult(
            query = query,
            resultCount = resultCount,
            responseText = textBuilder.toString(),
            citations = citations,
            sourceLabel = sourceLabel,
        )
    }

    private fun mergeStructuredSearchResult(
        query: String,
        resultCount: Int,
        responseText: String,
        citations: List<SearchResultItem>,
        sourceLabel: String,
    ): SearchResult {
        val structured = parseStructuredResult(
            text = responseText,
            sourceLabel = sourceLabel,
        )
        val mergedItems = deduplicateSearchItems(structured.items + citations)
            .take(resultCount)
        return SearchResult(
            query = query,
            answer = structured.answer,
            items = mergedItems,
        )
    }

    private fun parseStructuredResult(
        text: String,
        sourceLabel: String,
    ): StructuredSearchPayload {
        val jsonText = extractJsonObject(text)
            ?: return StructuredSearchPayload(
                answer = text.trim(),
                items = emptyList(),
            )
        val root = runCatching { JsonParser.parseString(jsonText).asJsonObject }.getOrNull()
            ?: return StructuredSearchPayload(
                answer = text.trim(),
                items = emptyList(),
            )
        val answer = root.getString("answer").trim()
        val items = when {
            root.getAsJsonArray("items") != null -> root.getAsJsonArray("items")
            else -> root.getAsJsonArray("results")
        }.orEmpty().mapNotNull { itemElement ->
            val item = itemElement.asJsonObject
            val url = item.getString("url").trim()
            if (url.isBlank()) {
                return@mapNotNull null
            }
            SearchResultItem(
                id = item.getString("id").ifBlank { stableSearchResultId(url) },
                title = item.getString("title").ifBlank { url },
                url = url,
                snippet = item.getString("text").ifBlank { item.getString("snippet") },
                sourceLabel = item.getString("sourceLabel").ifBlank { sourceLabel },
            )
        }
        return StructuredSearchPayload(
            answer = answer.ifBlank {
                items.firstOrNull()?.snippet.orEmpty()
            },
            items = items,
        )
    }

    private fun extractJsonObject(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return null
        }
        val fenced = Regex("```json\\s*(\\{.*})\\s*```", RegexOption.DOT_MATCHES_ALL)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
        if (!fenced.isNullOrBlank()) {
            return fenced.trim()
        }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start == -1 || end <= start) {
            return null
        }
        return trimmed.substring(start, end + 1)
    }

    private fun buildResponsesUrl(provider: ProviderSettings): String {
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(
            provider.baseUrl,
            ProviderApiProtocol.OPENAI_COMPATIBLE,
        )
        return normalizedBaseUrl.removeSuffix("/") + "/responses"
    }

    private fun buildAnthropicUrl(provider: ProviderSettings): String {
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(
            provider.baseUrl,
            ProviderApiProtocol.ANTHROPIC,
        )
        return normalizedBaseUrl.removeSuffix("/") + "/messages"
    }

    private fun buildExtractionPrompt(
        query: String,
        resultCount: Int,
    ): String {
        return """
            你是一个联网搜索结果整理器。请使用可用的网页搜索工具搜索用户问题，并只输出一个 JSON 对象，不要输出 Markdown、解释或额外文字。
            JSON 结构必须严格为：
            {
              "query": "原查询",
              "answer": "对搜索结果的简短总结",
              "items": [
                {
                  "id": "稳定且简短的唯一 id",
                  "title": "来源标题",
                  "url": "https://example.com",
                  "text": "与问题最相关的摘要",
                  "sourceLabel": "LLM 搜索"
                }
              ]
            }
            要求：
            1. 最多返回 $resultCount 条结果。
            2. 必须优先返回真实网页来源，不要编造 URL。
            3. answer 用中文简洁总结最值得看的结论，不超过 140 字。
            4. text 用中文简洁概括，不超过 120 字。
            5. id 必须在本轮结果内唯一，优先使用稳定短 id。
            用户查询：$query
        """.trimIndent()
    }

    private fun JsonObject.getString(key: String): String {
        return get(key)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
    }

    private fun JsonObject.getAsJsonArray(key: String): JsonArray? {
        return get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    }

    private fun JsonElement?.orEmpty(): Iterable<JsonElement> {
        return when {
            this == null || this.isJsonNull -> emptyList()
            this.isJsonArray -> this.asJsonArray
            else -> emptyList()
        }
    }

    private fun deduplicateSearchItems(
        items: List<SearchResultItem>,
    ): List<SearchResultItem> {
        return items
            .groupBy { normalizeSearchUrl(it.url) }
            .values
            .map { group ->
                group.maxWithOrNull(
                    compareBy<SearchResultItem>(
                        { it.title.isNotBlank() },
                        { it.snippet.length },
                    ),
                ) ?: group.first()
            }
            .filter { it.url.isNotBlank() }
    }

    private fun normalizeSearchUrl(url: String): String {
        return runCatching {
            url.toHttpUrl()
                .newBuilder()
                .fragment(null)
                .build()
                .toString()
                .trimEnd('/')
        }.getOrDefault(url.trim().trimEnd('/'))
    }

    private fun stableSearchResultId(url: String): String {
        return normalizeSearchUrl(url)
            .hashCode()
            .toUInt()
            .toString(16)
            .takeLast(8)
    }

    private companion object {
        val gson = AppJson.gson
    }
}
