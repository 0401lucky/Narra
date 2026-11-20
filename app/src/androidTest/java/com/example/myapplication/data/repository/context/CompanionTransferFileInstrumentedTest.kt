package com.example.myapplication.data.repository.context

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.CompanionImageKind
import com.example.myapplication.model.CompanionImageResource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CompanionTransferFileInstrumentedTest {
    @Test
    fun privateHashPublicationAndPlanReceipt_areDurableAndNeverOverwriteChangedFile() =
        runBlocking<Unit> {
            val base: Context = ApplicationProvider.getApplicationContext()
            val cache = base.cacheDir.canonicalFile
            val root =
                File(cache, "d6-transfer-${UUID.randomUUID().toString().take(8)}").canonicalFile
            check(root.parentFile == cache)
            val context = object : ContextWrapper(base) {
                override fun getFilesDir(): File = File(root, "f").apply { mkdirs() }
                override fun getApplicationContext(): Context = this
            }
            try {
                val source = File(context.filesDir, "synthetic.png")
                val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(Color.rgb(23, 98, 56)) }
                try {
                    source.outputStream()
                        .use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally {
                    bitmap.recycle()
                }
                val store = PrivateCompanionTransferFileStore(context)
                val image = store.readImage(
                    CompanionImageResource(
                        "source",
                        "合成立绘",
                        source.path,
                        CompanionImageKind.PORTRAIT,
                        2,
                        3
                    )
                )
                val plan = store.freezePlan("local-plan", "{\"version\":1,\"synthetic\":true}")
                assertTrue(plan.created)
                assertEquals(
                    "{\"version\":1,\"synthetic\":true}",
                    store.readPlan("local-plan", plan.sha256)
                )
                assertFalse(
                    store.freezePlan(
                        "local-plan",
                        "{\"version\":1,\"synthetic\":true}"
                    ).created
                )
                val competingJson = "{\"version\":1,\"competing\":true}"
                val competing = (1..2).map {
                    async(Dispatchers.IO) {
                        PrivateCompanionTransferFileStore(context).freezePlan(
                            "competing-plan",
                            competingJson
                        )
                    }
                }.awaitAll()
                assertEquals(1, competing.count { it.created })
                assertEquals(
                    competingJson,
                    PrivateCompanionTransferFileStore(context).readPlan(
                        "competing-plan",
                        competing.first().sha256
                    )
                )
                assertTrue(runCatching { store.freezePlan("competing-plan", "changed") }.isFailure)
                assertEquals(
                    competingJson,
                    store.readPlan("competing-plan", competing.first().sha256)
                )
                val blockedDirectory = File(
                    context.filesDir,
                    "companion_transfers/plans/blocked-plan"
                ).apply { mkdirs() }
                val retained = File(
                    blockedDirectory,
                    "retained.txt"
                ).apply { writeText("synthetic retained bytes") }
                assertTrue(runCatching {
                    store.freezePlan(
                        "blocked-plan",
                        competingJson
                    )
                }.isFailure)
                assertEquals("synthetic retained bytes", retained.readText())
                assertFalse(File(blockedDirectory, "plan.json").exists())
                val legacyJson = "{\"version\":1,\"legacy\":true}"
                val legacyPlan = File(
                    context.filesDir,
                    "companion_transfers/plans/legacy-plan.json"
                ).apply { writeText(legacyJson) }
                val legacyHash = CompanionTransferImageValidation.sha256(legacyJson.toByteArray())
                assertEquals(legacyJson, store.readPlan("legacy-plan", legacyHash))
                assertFalse(store.freezePlan("legacy-plan", legacyJson).created)
                assertTrue(runCatching { store.freezePlan("legacy-plan", "changed") }.isFailure)
                assertEquals(legacyJson, legacyPlan.readText())
                val competingImages = (1..2).map {
                    async(Dispatchers.IO) {
                        PrivateCompanionTransferFileStore(context).publishImages(
                            listOf(image)
                        ).single()
                    }
                }.awaitAll()
                assertEquals(1, competingImages.map { it.path }.distinct().size)
                val published = competingImages.first()
                val file = File(published.path)
                assertTrue(file.isFile)
                assertEquals(
                    image.sha256,
                    CompanionTransferImageValidation.sha256(file.readBytes())
                )
                val decoded = requireNotNull(BitmapFactory.decodeFile(file.path))
                try {
                    assertEquals(Color.rgb(23, 98, 56), decoded.getPixel(0, 0))
                } finally {
                    decoded.recycle()
                }
                val timestamp = file.lastModified()
                store.validatePublishedImages(listOf(published))
                assertEquals(published, store.publishImages(listOf(image)).single())
                assertEquals(timestamp, file.lastModified())
                file.writeText("synthetic changed file")
                assertTrue(runCatching { store.publishImages(listOf(image)) }.isFailure)
                assertTrue(runCatching { store.validatePublishedImages(listOf(published)) }.isFailure)
                assertEquals("synthetic changed file", file.readText())
                val legacyImage =
                    File(context.filesDir, "companion_transfers/assets/${image.sha256}.png").apply {
                        writeBytes(
                            CompanionTransferImageValidation.bytes(
                                image
                            )
                        )
                    }
                val reusedLegacy = store.publishImages(listOf(image)).single()
                assertEquals(legacyImage.canonicalPath, reusedLegacy.path)
                store.validatePublishedImages(listOf(reusedLegacy))
                legacyImage.writeText("synthetic changed legacy file")
                assertTrue(runCatching { store.publishImages(listOf(image)) }.isFailure)
                assertEquals("synthetic changed legacy file", legacyImage.readText())
                assertEquals("synthetic changed file", file.readText())
                store.discardPlan("local-plan", plan.sha256)
                store.discardPlan("competing-plan", competing.first().sha256)
                store.discardPlan("legacy-plan", legacyHash)
                assertFalse(legacyPlan.exists())
                assertTrue(source.isFile)
                assertEquals("synthetic changed file", file.readText())
                assertEquals("synthetic retained bytes", retained.readText())
                assertTrue(
                    File(context.filesDir, "companion_transfers/plans").listFiles().orEmpty()
                        .none { it.name.endsWith(".tmp") })
            } finally {
                check(root.parentFile == cache && root.name.startsWith("d6-transfer-"))
                check(root.deleteRecursively())
            }
        }
}
