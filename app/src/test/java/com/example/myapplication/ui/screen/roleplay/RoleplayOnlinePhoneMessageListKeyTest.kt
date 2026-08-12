package com.example.myapplication.ui.screen.roleplay

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayOnlinePhoneMessageListKeyTest {
    @Test
    fun roleplayLazyLists_useUiItemIdAndStableContentType() {
        val sourceFiles = listOf(
            "ui/component/roleplay/RoleplayDialoguePanel.kt",
            "ui/screen/roleplay/RoleplayOnlinePhoneContent.kt",
            "ui/screen/roleplay/RoleplayReadingMode.kt",
        ).map(::resolveMainSource)

        sourceFiles.forEach { sourceFile ->
            val source = sourceFile.readText()
            assertTrue(
                "${sourceFile.name} 必须只用 uiItemId 作为 Lazy key",
                source.contains("key = { _, message -> message.uiItemId }"),
            )
            assertTrue(
                "${sourceFile.name} 必须声明稳定 contentType",
                source.contains(
                    "contentType = { _, message -> roleplayLazyContentType(message) }",
                ),
            )
            assertFalse(
                "${sourceFile.name} 的 Lazy identity 不得包含正文哈希",
                source.contains("content.hashCode()"),
            )
        }
    }

    @Test
    fun onlinePhoneList_doesNotRetainLegacyIndexKeyBuilder() {
        val source = resolveMainSource(
            "ui/screen/roleplay/RoleplayOnlinePhoneContent.kt",
        ).readText()

        assertFalse(source.contains("buildOnlinePhoneMessageListKey"))
    }

    private fun resolveMainSource(relativePath: String): File {
        return sequenceOf(
            File("src/main/java/com/example/myapplication", relativePath),
            File("app/src/main/java/com/example/myapplication", relativePath),
        ).first { file -> file.exists() }
    }
}
