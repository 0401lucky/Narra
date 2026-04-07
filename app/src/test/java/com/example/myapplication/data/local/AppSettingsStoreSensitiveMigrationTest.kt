package com.example.myapplication.data.local

import com.example.myapplication.model.SearchSettings
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.model.SearchSourceIds
import com.example.myapplication.model.SearchSourceType
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsStoreSensitiveMigrationTest {
    private val gson = Gson()

    @Test
    fun migrate_movesLegacySearchApiKeysIntoSecureStorage() {
        val rawJson = gson.toJson(
            SearchSettings(
                sources = listOf(
                    SearchSourceConfig(
                        id = SearchSourceIds.BRAVE,
                        type = SearchSourceType.BRAVE,
                        enabled = true,
                        apiKey = "brave-key",
                    ),
                    SearchSourceConfig(
                        id = SearchSourceIds.TAVILY,
                        type = SearchSourceType.TAVILY,
                        enabled = true,
                        apiKey = "tavily-key",
                    ),
                ),
            ),
        )

        val result = SearchSettingsSensitiveMigrationSupport.migrate(
            rawJson = rawJson,
            existingSecureApiKeys = emptyMap(),
        )

        assertNotNull(result)
        assertEquals("brave-key", result?.mergedSecureApiKeys?.get(SearchSourceIds.BRAVE))
        assertEquals("tavily-key", result?.mergedSecureApiKeys?.get(SearchSourceIds.TAVILY))
        assertTrue(result?.sanitizedJson?.contains("brave-key") == false)
        assertTrue(result?.sanitizedJson?.contains("tavily-key") == false)
    }

    @Test
    fun migrate_keepsExistingSecureApiKeysWhenLegacyJsonStillContainsPlaintext() {
        val rawJson = gson.toJson(
            SearchSettings(
                sources = listOf(
                    SearchSourceConfig(
                        id = SearchSourceIds.BRAVE,
                        type = SearchSourceType.BRAVE,
                        enabled = true,
                        apiKey = "legacy-key",
                    ),
                ),
            ),
        )

        val result = SearchSettingsSensitiveMigrationSupport.migrate(
            rawJson = rawJson,
            existingSecureApiKeys = mapOf(
                SearchSourceIds.BRAVE to "secure-key",
            ),
        )

        assertNotNull(result)
        assertEquals("secure-key", result?.mergedSecureApiKeys?.get(SearchSourceIds.BRAVE))
        assertTrue(result?.sanitizedJson?.contains("legacy-key") == false)
    }

    @Test
    fun migrate_returnsNullWhenSearchSettingsJsonIsInvalid() {
        val result = SearchSettingsSensitiveMigrationSupport.migrate(
            rawJson = "{invalid-json",
            existingSecureApiKeys = emptyMap(),
        )

        assertNull(result)
    }
}
