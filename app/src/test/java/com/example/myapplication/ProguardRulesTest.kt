package com.example.myapplication

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProguardRulesTest {
    @Test
    fun proguardRules_keepRepositoryGsonDtoFieldNames() {
        val rules = resolveProjectFile("proguard-rules.pro").readText()

        assertTrue(rules.contains("com.example.myapplication.data.repository.tts.MimoTtsRequest"))
        assertTrue(rules.contains("com.example.myapplication.data.repository.tts.MimoTtsResponseAudio"))
        assertTrue(rules.contains("com.example.myapplication.data.repository.phone.Phone*Payload"))
    }

    private fun resolveProjectFile(path: String): File {
        return sequenceOf(
            File(path),
            File("app", path),
        ).first { it.exists() }
    }
}
