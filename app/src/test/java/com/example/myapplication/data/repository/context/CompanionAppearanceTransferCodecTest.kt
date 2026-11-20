package com.example.myapplication.data.repository.context

import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

class CompanionAppearanceTransferCodecTest {
    private val codec = CompanionAppearanceTransferCodec()

    @Test
    fun packageRoundTrip_preservesContentMappingWithoutPrivateDomains() {
        val pack = syntheticAppearance()
        val raw = codec.encode(pack)
        assertEquals(pack, codec.decode(raw))
        val root = JsonParser.parseString(raw).asJsonObject
        assertEquals(setOf("version", "visuals", "images"), root.keySet())
        for (privateField in listOf(
            "systemPrompt",
            "extraStyle",
            "memoryArchive",
            "apiKey",
            "samplePath",
            "path",
            "sceneOverrides"
        )) assertFalse(root.toString().contains("\"$privateField\""))
        assertFalse(raw.contains("/data/"))
    }

    @Test
    fun unknownVersionFieldsDuplicatesAndTrailingJson_areRejectedAsWholePackage() {
        val raw = codec.encode(syntheticAppearance())
        assertTrue(raw.startsWith("{"))
        val altered = listOf(
            changedJson(raw) { it.addProperty("version", 9) },
            changedJson(raw) { it.addProperty("apiKey", "private-sentinel") },
            "{\"version\":1," + raw.removePrefix("{"),
            raw + "{}"
        )
        altered.forEachIndexed { index, candidate ->
            assertNotEquals(raw, candidate)
            assertTrue(
                "invalid input $index",
                runCatching { codec.decode(candidate) }.exceptionOrNull() is IllegalArgumentException
            )
        }
    }

    @Test
    fun badHashOrCrossResourceReference_cannotBePartiallyImported() {
        val pack = syntheticAppearance()
        val broken = listOf(
            pack.copy(images = listOf(pack.images.single().copy(sha256 = "0".repeat(64)))),
            pack.copy(visuals = pack.visuals.copy(selectedBackgroundId = "source_image")),
            pack.copy(
                visuals = pack.visuals.copy(
                    outfits = listOf(
                        pack.visuals.outfits.single()
                            .copy(expressions = mapOf("happy" to "missing"))
                    )
                )
            )
        )
        broken.forEach { assertTrue(runCatching { codec.decode(AppJson.gson.toJson(it)) }.isFailure) }
    }

    @Test
    fun ancillaryPrivateTextAndArbitraryPaths_areRejected() {
        val image = syntheticTransferImage(syntheticTransferPng(metadata = true))
        assertTrue(runCatching { codec.decode(AppJson.gson.toJson(syntheticAppearance(image))) }.isFailure)
        val raw = changedJson(codec.encode(syntheticAppearance())) {
            it.getAsJsonObject("visuals").getAsJsonArray("resources")[0].asJsonObject.addProperty(
                "path",
                "/data/private/clone.wav"
            )
        }
        assertTrue(runCatching { codec.decode(raw) }.isFailure)
    }

    @Test
    fun malformedAllowedAncillaryChunks_cannotSmugglePrivateTextThroughPreview() {
        val secret = "private_path\u0000/data/private/voice-clone-sample-secret.wav".toByteArray()
        for (type in listOf("sRGB", "gAMA", "cHRM", "sBIT", "pHYs", "tRNS", "PLTE")) {
            val bytes = modifiedPng(
                type,
                if (type == "PLTE") secret.copyOf(769) else secret,
                insertBeforePixels = true
            )
            val raw = AppJson.gson.toJson(syntheticAppearance(syntheticTransferImage(bytes)))
            assertTrue(
                type,
                runCatching { codec.decode(raw) }.exceptionOrNull() is IllegalArgumentException
            )
        }
    }

    @Test
    fun corruptDeflateInvalidFilterAndWrongPixelLength_areRejectedBeforeImport() {
        val invalidPixels = listOf(
            byteArrayOf(1, 2, 3),
            deflated(ByteArray(26)),
            deflated(ByteArray(28)),
            deflated(ByteArray(27).apply { this[0] = 5 }),
        )
        invalidPixels.forEach { pixels ->
            val raw = AppJson.gson.toJson(
                syntheticAppearance(
                    syntheticTransferImage(
                        modifiedPng(
                            "IDAT",
                            pixels
                        )
                    )
                )
            )
            assertTrue(runCatching { codec.decode(raw) }.exceptionOrNull() is IllegalArgumentException)
        }
        val invalidHeader = syntheticTransferPng().copyOfRange(16, 29).apply { this[10] = 1 }
        assertTrue(runCatching {
            codec.decode(
                AppJson.gson.toJson(
                    syntheticAppearance(
                        syntheticTransferImage(modifiedPng("IHDR", invalidHeader))
                    )
                )
            )
        }.isFailure)
    }

    @Test
    fun paletteAndInterlacedPixels_remainValidPortableAppearanceContent() {
        val header = syntheticTransferPng().copyOfRange(16, 29)
        val indexed = modifiedPng("IHDR", header.copyOf().apply { this[8] = 1; this[9] = 3 })
            .let { modifiedPng("IDAT", deflated(ByteArray(6)), source = it) }
            .let {
                modifiedPng(
                    "PLTE",
                    byteArrayOf(23, 98, 56),
                    insertBeforePixels = true,
                    source = it
                )
            }
        val interlaced = modifiedPng("IHDR", header.copyOf().apply { this[12] = 1 })
            .let { modifiedPng("IDAT", deflated(ByteArray(29)), source = it) }
        for (bytes in listOf(indexed, interlaced)) {
            val pack = syntheticAppearance(syntheticTransferImage(bytes))
            assertEquals(pack, codec.decode(codec.encode(pack)))
        }
    }

    @Test
    fun sharedPixels_haveOneContentEntryAndSeparatePermittedKinds() {
        val pack = syntheticAppearance()
        val image = pack.images.single()
        val resources = pack.visuals.resources + CompanionAppearanceResource(
            "bg",
            "背景",
            image.sha256,
            CompanionImageKind.BACKGROUND
        )
        val shared = pack.copy(
            visuals = pack.visuals.copy(
                resources = resources,
                selectedBackgroundId = "bg"
            )
        )
        assertEquals(1, codec.decode(codec.encode(shared)).images.size)
        assertEquals(2, codec.decode(codec.encode(shared)).visuals.resources.size)
    }

    @Test
    fun privateV3_preservesAuthorizedMemoryAndPreferencesWhileOldMissingDomainStaysNull() {
        val companion = CompanionContextTransferEntry(
            "alice",
            zoneId = "Asia/Shanghai",
            writing = CompanionWritingSettings(extraStyle = "私人写法"),
            voice = CompanionVoicePreferences(),
            appearance = syntheticAppearance()
        )
        val bundle = ContextDataBundle(
            version = 3,
            exportedAt = 4L,
            assistants = listOf(Assistant(id = "alice", systemPrompt = "私人角色设定")),
            memoryEntries = listOf(MemoryEntry(id = "manual", content = "已授权人工记忆")),
            companions = listOf(companion)
        )
        val transfer = ContextTransferCodec()
        val restored = transfer.decode(transfer.encode(bundle))
        assertEquals(bundle.companions, restored.companions)
        assertEquals("已授权人工记忆", restored.memoryEntries.single().content)
        assertEquals("私人角色设定", restored.assistants.single().systemPrompt)
        for (version in 1..2) assertNull(
            transfer.decode(
                transfer.encode(
                    bundle.copy(
                        version = version,
                        companions = null
                    )
                )
            ).companions
        )
        assertTrue(runCatching {
            transfer.decode(
                changedJson(transfer.encode(bundle)) { it.addProperty("version", 4) }
            )
        }.isFailure)
    }

    @Test
    fun privateFutureDomainVoiceAndUnknownSamples_areRejectedBeforeSettings() {
        val entry = CompanionContextTransferEntry("alice", voice = CompanionVoicePreferences())
        val transfer = ContextTransferCodec()
        val raw = transfer.encode(
            ContextDataBundle(
                version = 3,
                exportedAt = 1L,
                companions = listOf(entry)
            )
        )
        val altered = listOf(
            changedJson(raw) {
                it.getAsJsonArray("companions")[0].asJsonObject.addProperty(
                    "schemaVersion",
                    8
                )
            },
            changedJson(raw) {
                it.getAsJsonArray("companions")[0].asJsonObject.getAsJsonObject("voice")
                    .addProperty("schemaVersion", 8)
            },
            changedJson(raw) {
                it.getAsJsonArray("companions")[0].asJsonObject.getAsJsonObject("voice")
                    .addProperty("cloneSamplePath", "/private/sample.wav")
            },
            changedJson(raw) { it.addProperty("version", 2) },
        )
        altered.forEach { assertTrue(runCatching { transfer.decode(it) }.isFailure) }
    }

    private fun changedJson(raw: String, change: (JsonObject) -> Unit): String {
        val root = JsonParser.parseString(raw).asJsonObject
        val before = root.deepCopy()
        change(root)
        assertNotEquals(before, root)
        return AppJson.gson.toJson(root)
    }

    private fun deflated(pixels: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        DeflaterOutputStream(output).use { it.write(pixels) }
    }.toByteArray()

    private fun modifiedPng(
        type: String,
        payload: ByteArray,
        insertBeforePixels: Boolean = false,
        source: ByteArray = syntheticTransferPng()
    ): ByteArray {
        val result = ByteArrayOutputStream()
        DataInputStream(ByteArrayInputStream(source)).use { input ->
            DataOutputStream(result).use { output ->
                val signature = ByteArray(8).also(input::readFully)
                output.write(signature)
                fun chunk(name: String, data: ByteArray) {
                    val label = name.toByteArray(Charsets.US_ASCII)
                    output.writeInt(data.size)
                    output.write(label)
                    output.write(data)
                    output.writeInt(CRC32().apply { update(label); update(data) }.value.toInt())
                }
                while (input.available() > 0) {
                    val length = input.readInt()
                    val name = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
                    val data = ByteArray(length).also(input::readFully)
                    input.readInt()
                    if (insertBeforePixels && name == "IDAT") chunk(type, payload)
                    chunk(name, if (!insertBeforePixels && name == type) payload else data)
                }
            }
        }
        return result.toByteArray()
    }
}
