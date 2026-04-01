package com.example.myapplication.model

const val DEFAULT_SEARCH_RESULT_COUNT = 5
const val MIN_SEARCH_RESULT_COUNT = 1
const val MAX_SEARCH_RESULT_COUNT = 10

object SearchSourceIds {
    const val BRAVE = "search-source-brave"
    const val TAVILY = "search-source-tavily"
    const val GOOGLE_CSE = "search-source-google-cse"
    const val LLM_SEARCH = "search-source-llm-search"
}

enum class SearchSourceType(
    val label: String,
) {
    BRAVE("Brave"),
    TAVILY("Tavily"),
    GOOGLE_CSE("Google CSE"),
    LLM_SEARCH("LLM 搜索"),
}

data class SearchSourceConfig(
    val id: String,
    val type: SearchSourceType,
    val name: String = type.label,
    val enabled: Boolean = false,
    val apiKey: String = "",
    val engineId: String = "",
    val providerId: String = "",
) {
    fun isConfigured(): Boolean {
        if (!enabled) {
            return false
        }
        return when (type) {
            SearchSourceType.BRAVE,
            SearchSourceType.TAVILY,
            -> apiKey.isNotBlank()

            SearchSourceType.GOOGLE_CSE -> engineId.isNotBlank()
                && apiKey.isNotBlank()

            SearchSourceType.LLM_SEARCH -> providerId.isNotBlank()
        }
    }

    fun isReady(activeProvider: ProviderSettings?): Boolean {
        return when (type) {
            SearchSourceType.LLM_SEARCH -> {
                enabled &&
                    activeProvider?.hasBaseCredentials() == true &&
                    activeProvider.supportsLlmSearchSource() &&
                    activeProvider.resolveFunctionModel(ProviderFunction.SEARCH).isNotBlank()
            }

            else -> isConfigured()
        }
    }
}

data class SearchSettings(
    val sources: List<SearchSourceConfig> = defaultSearchSources(),
    val selectedSourceId: String = SearchSourceIds.BRAVE,
    val defaultResultCount: Int = DEFAULT_SEARCH_RESULT_COUNT,
) {
    fun normalized(): SearchSettings {
        val defaultSources = defaultSearchSources()
        val defaultIds = defaultSources.map(SearchSourceConfig::id).toSet()
        val currentById = sources.associateBy(SearchSourceConfig::id)
        val mergedSources = buildList {
            defaultSources.forEach { defaultSource ->
                val current = currentById[defaultSource.id]
                if (current == null) {
                    add(defaultSource)
                } else {
                    add(
                        current.copy(
                            name = current.name.ifBlank { defaultSource.name },
                        ),
                    )
                }
            }
            sources
                .filterNot { it.id in defaultIds }
                .forEach(::add)
        }
        val resolvedSelectedSourceId = mergedSources.firstOrNull { it.id == selectedSourceId }?.id
            ?: mergedSources.firstOrNull()?.id
            .orEmpty()
        return copy(
            sources = mergedSources,
            selectedSourceId = resolvedSelectedSourceId,
            defaultResultCount = defaultResultCount.coerceIn(
                MIN_SEARCH_RESULT_COUNT,
                MAX_SEARCH_RESULT_COUNT,
            ),
        )
    }

    fun selectedSourceOrNull(): SearchSourceConfig? {
        val normalized = normalized()
        return normalized.sources.firstOrNull { it.id == normalized.selectedSourceId }
    }

    fun activeSourceOrNull(
        activeProvider: ProviderSettings? = null,
    ): SearchSourceConfig? {
        val normalized = normalized()
        return normalized.selectedSourceOrNull()?.takeIf { it.isReady(activeProvider) }
    }
}

fun defaultSearchSources(): List<SearchSourceConfig> {
    return listOf(
        SearchSourceConfig(
            id = SearchSourceIds.BRAVE,
            type = SearchSourceType.BRAVE,
            name = "Brave 搜索",
        ),
        SearchSourceConfig(
            id = SearchSourceIds.TAVILY,
            type = SearchSourceType.TAVILY,
            name = "Tavily 搜索",
        ),
        SearchSourceConfig(
            id = SearchSourceIds.GOOGLE_CSE,
            type = SearchSourceType.GOOGLE_CSE,
            name = "Google CSE",
        ),
        SearchSourceConfig(
            id = SearchSourceIds.LLM_SEARCH,
            type = SearchSourceType.LLM_SEARCH,
            name = "LLM 搜索",
        ),
    )
}
