package com.example.myapplication

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesTest {
    @Test
    fun backupRules_excludeVoiceDirectories() {
        val exclusions = readExclusions("src/main/res/xml/backup_rules.xml")

        assertTrue(Exclusion("full-backup-content", "file", "generated_voice_messages/") in exclusions)
        assertTrue(Exclusion("full-backup-content", "file", "voice_clone_samples/") in exclusions)
    }

    @Test
    fun dataExtractionRules_excludeVoiceDirectoriesFromBackupAndTransfer() {
        val exclusions = readExclusions("src/main/res/xml/data_extraction_rules.xml")

        assertTrue(Exclusion("cloud-backup", "file", "generated_voice_messages/") in exclusions)
        assertTrue(Exclusion("cloud-backup", "file", "voice_clone_samples/") in exclusions)
        assertTrue(Exclusion("device-transfer", "file", "generated_voice_messages/") in exclusions)
        assertTrue(Exclusion("device-transfer", "file", "voice_clone_samples/") in exclusions)
    }

    private fun readExclusions(path: String): Set<Exclusion> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(resolveProjectFile(path))
        val nodes = document.getElementsByTagName("exclude")
        return buildSet {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                add(
                    Exclusion(
                        scope = element.parentNode.nodeName,
                        domain = element.getAttribute("domain"),
                        path = element.getAttribute("path"),
                    ),
                )
            }
        }
    }

    private fun resolveProjectFile(path: String): File {
        return sequenceOf(
            File(path),
            File("app", path),
        ).first { it.exists() }
    }

    private data class Exclusion(
        val scope: String,
        val domain: String,
        val path: String,
    )
}
