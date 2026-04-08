package com.example.myapplication.data.repository.search

import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.model.SearchSourceType
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class SearchResult(
    val query: String,
    val answer: String = "",
    val items: List<SearchResultItem> = emptyList(),
)

data class SearchResultItem(
    val id: String = "",
    val title: String,
    val url: String,
    val snippet: String,
    val sourceLabel: String,
)

fun SearchResult.toCitations(): List<MessageCitation> {
    return items.map { item ->
        MessageCitation(
            id = item.id,
            title = item.title,
            url = item.url,
            sourceLabel = item.sourceLabel,
        )
    }
}

interface SearchRepository {
    suspend fun search(
        source: SearchSourceConfig,
        query: String,
        resultCount: Int,
    ): SearchResult
}

class DefaultSearchRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val llmSearchExecutor: SearchModelExecutor? = null,
) : SearchRepository {
    override suspend fun search(
        source: SearchSourceConfig,
        query: String,
        resultCount: Int,
    ): SearchResult = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotBlank()) { "搜索词不能为空" }
        require(source.isConfigured()) { "当前搜索源尚未配置完成" }
        when (source.type) {
            SearchSourceType.BRAVE -> searchBrave(source, normalizedQuery, resultCount)
            SearchSourceType.TAVILY -> searchTavily(source, normalizedQuery, resultCount)
            SearchSourceType.GOOGLE_CSE -> searchGoogleCse(source, normalizedQuery, resultCount)
            SearchSourceType.LLM_SEARCH -> {
                val executor = llmSearchExecutor
                    ?: throw IllegalStateException("当前环境未配置 LLM 搜索执行器")
                executor.search(
                    source = source,
                    query = normalizedQuery,
                    resultCount = resultCount,
                )
            }
        }
    }

    private fun searchBrave(
        source: SearchSourceConfig,
        query: String,
        resultCount: Int,
    ): SearchResult {
        val url = "https://api.search.brave.com/res/v1/web/search".toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("count", resultCount.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("X-Subscription-Token", source.apiKey.trim())
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Brave 搜索失败：${response.code}")
            }
            val json = JsonParser.parseString(body).asJsonObject
            val items = json.getAsJsonObject("web")
                ?.getAsJsonArray("results")
                .orEmpty()
                .mapNotNull { itemElement ->
                    val item = itemElement.asJsonObject
                    val title = item.get("title")?.asString.orEmpty().trim()
                    val urlValue = item.get("url")?.asString.orEmpty().trim()
                    if (title.isBlank() || urlValue.isBlank()) {
                        return@mapNotNull null
                    }
                    SearchResultItem(
                        id = stableSearchResultId(urlValue),
                        title = title,
                        url = urlValue,
                        snippet = item.get("description")?.asString.orEmpty().trim(),
                        sourceLabel = source.name.ifBlank { source.type.label },
                    )
                }
            SearchResult(
                query = query,
                answer = items.firstOrNull()?.snippet.orEmpty(),
                items = deduplicateSearchItems(items).take(resultCount),
            )
        }
    }

    private fun searchTavily(
        source: SearchSourceConfig,
        query: String,
        resultCount: Int,
    ): SearchResult {
        val payload = """
            {
              "api_key": ${queryString(source.apiKey.trim())},
              "query": ${queryString(query)},
              "max_results": $resultCount,
              "search_depth": "basic",
              "include_answer": false,
              "include_raw_content": false
            }
        """.trimIndent()
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .addHeader("Accept", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Tavily 搜索失败：${response.code}")
            }
            val json = JsonParser.parseString(body).asJsonObject
            val items = json.getAsJsonArray("results")
                .orEmpty()
                .mapNotNull { itemElement ->
                    val item = itemElement.asJsonObject
                    val title = item.get("title")?.asString.orEmpty().trim()
                    val urlValue = item.get("url")?.asString.orEmpty().trim()
                    if (title.isBlank() || urlValue.isBlank()) {
                        return@mapNotNull null
                    }
                    SearchResultItem(
                        id = stableSearchResultId(urlValue),
                        title = title,
                        url = urlValue,
                        snippet = item.get("content")?.asString.orEmpty().trim(),
                        sourceLabel = source.name.ifBlank { source.type.label },
                    )
                }
            SearchResult(
                query = query,
                answer = items.firstOrNull()?.snippet.orEmpty(),
                items = deduplicateSearchItems(items).take(resultCount),
            )
        }
    }

    private fun searchGoogleCse(
        source: SearchSourceConfig,
        query: String,
        resultCount: Int,
    ): SearchResult {
        val url = "https://customsearch.googleapis.com/customsearch/v1".toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", source.apiKey.trim())
            .addQueryParameter("cx", source.engineId.trim())
            .addQueryParameter("q", query)
            .addQueryParameter("num", resultCount.coerceIn(1, 10).toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Google CSE 搜索失败：${response.code}")
            }
            val json = JsonParser.parseString(body).asJsonObject
            val items = json.getAsJsonArray("items")
                .orEmpty()
                .mapNotNull { itemElement ->
                    val item = itemElement.asJsonObject
                    val title = item.get("title")?.asString.orEmpty().trim()
                    val urlValue = item.get("link")?.asString.orEmpty().trim()
                    if (title.isBlank() || urlValue.isBlank()) {
                        return@mapNotNull null
                    }
                    SearchResultItem(
                        id = stableSearchResultId(urlValue),
                        title = title,
                        url = urlValue,
                        snippet = item.get("snippet")?.asString.orEmpty().trim(),
                        sourceLabel = source.name.ifBlank { source.type.label },
                    )
                }
            SearchResult(
                query = query,
                answer = items.firstOrNull()?.snippet.orEmpty(),
                items = deduplicateSearchItems(items).take(resultCount),
            )
        }
    }

    private fun Iterable<com.google.gson.JsonElement>?.orEmpty(): Iterable<com.google.gson.JsonElement> {
        return this ?: emptyList()
    }

    private fun queryString(value: String): String {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"") + "\""
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
            val parsed = url.toHttpUrl()
            parsed.newBuilder()
                .fragment(null)
                .build()
                .toString()
                .trimEnd('/')
        }.getOrDefault(url.trim().trimEnd('/'))
    }

    private fun stableSearchResultId(url: String): String {
        val normalized = normalizeSearchUrl(url)
        return normalized.hashCode()
            .toUInt()
            .toString(16)
            .takeLast(8)
    }
}
