package com.example.myapplication.testutil

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.myapplication.data.local.AppSettingsStore
import com.example.myapplication.data.local.SettingsSecureValueStore
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.repository.context.*
import com.example.myapplication.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

internal fun syntheticTransferPng(
    width: Int = 2,
    height: Int = 3,
    metadata: Boolean = false,
    randomSeed: Int? = null
): ByteArray {
    val result = ByteArrayOutputStream()
    DataOutputStream(result).use { output ->
        output.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            val name = type.toByteArray(Charsets.US_ASCII)
            output.writeInt(data.size); output.write(name); output.write(data)
            output.writeInt(CRC32().apply { update(name); update(data) }.value.toInt())
        }

        val header = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use {
                it.writeInt(width); it.writeInt(height); it.write(byteArrayOf(8, 6, 0, 0, 0))
            }
        }.toByteArray()
        chunk("IHDR", header)
        if (metadata) chunk(
            "tEXt",
            "private_path\u0000/data/private/voice-clone-sample-secret.wav".toByteArray()
        )
        val pixels = ByteArrayOutputStream().also { bytes ->
            DeflaterOutputStream(bytes).use { compressed ->
                if (randomSeed == null) repeat(height) {
                    compressed.write(0); repeat(width) {
                    compressed.write(
                        byteArrayOf(23, 98, 56, 255.toByte())
                    )
                }
                }
                else {
                    val random = Random(randomSeed.toLong())
                    val row = ByteArray(width * 4)
                    repeat(height) {
                        compressed.write(0); random.nextBytes(row); compressed.write(
                        row
                    )
                    }
                }
            }
        }.toByteArray()
        chunk("IDAT", pixels); chunk("IEND", byteArrayOf())
    }
    return result.toByteArray()
}

internal fun syntheticTransferImage(
    bytes: ByteArray = syntheticTransferPng(),
    width: Int = 2,
    height: Int = 3
): CompanionTransferImage =
    CompanionTransferImage(
        CompanionTransferImageValidation.sha256(bytes),
        "image/png",
        width,
        height,
        Base64.getEncoder().encodeToString(bytes)
    )

internal fun syntheticAppearance(image: CompanionTransferImage = syntheticTransferImage()): CompanionAppearancePackage =
    CompanionAppearancePackage(
        visuals = CompanionAppearanceData(
            preferredMode = CompanionDisplayMode.STAGE,
            resources = listOf(
                CompanionAppearanceResource(
                    "source_image",
                    "春装立绘",
                    image.sha256,
                    CompanionImageKind.PORTRAIT
                )
            ),
            outfits = listOf(
                CompanionOutfit(
                    "source_outfit",
                    "春装",
                    "source_image",
                    mapOf("happy" to "source_image")
                )
            ),
            selectedOutfitId = "source_outfit",
        ), images = listOf(image)
    )

/** 独占短临时目录，Room 与 DataStore 都是真实文件；避免 Windows 方法名长路径。 */
internal class CompanionTransferFixture : AutoCloseable {
    private val temporaryParent = File(System.getProperty("java.io.tmpdir")).canonicalFile
    private val root =
        Files.createTempDirectory(temporaryParent.toPath(), "d6t-").toFile().canonicalFile
    val context: Context =
        object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
            override fun getFilesDir(): File = File(root, "f").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "c").apply { mkdirs() }
            override fun getDatabasePath(name: String): File = File(root, name)
            override fun getApplicationContext(): Context = this
        }
    private var settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var settings = makeSettings()
        private set
    lateinit var database: ChatDatabase
        private set
    lateinit var worlds: RoomWorldBookRepository
        private set
    lateinit var presets: RoomPresetRepository
        private set
    lateinit var archive: RoomMemoryArchiveRepository
        private set
    lateinit var memoryTransfer: RoomMemoryArchiveTransferRepository
        private set
    val files: CompanionTransferFileStore = PrivateCompanionTransferFileStore(context)
    var now = 1_700_000_000_000L

    init {
        openDatabase()
    }

    private fun makeSettings(): AppSettingsStore {
        val data = PreferenceDataStoreFactory.create(
            scope = settingsScope,
            produceFile = { File(root, "s.preferences_pb") })
        val secure = object : SettingsSecureValueStore {
            override fun getString(key: String) = ""
            override fun putString(key: String, value: String) = Unit
            override fun getStringMap(key: String) = emptyMap<String, String>()
            override fun putStringMap(key: String, values: Map<String, String>) = Unit
        }
        return AppSettingsStore(data, secure, settingsScope, Dispatchers.IO)
    }

    private fun openDatabase() {
        database =
            Room.databaseBuilder(context, ChatDatabase::class.java, "r.db").allowMainThreadQueries()
                .addMigrations(*ChatDatabase.ALL_MIGRATIONS).build()
        worlds = RoomWorldBookRepository(database.worldBookDao())
        presets = RoomPresetRepository(database.presetDao(), builtInPresets = emptyList())
        archive = RoomMemoryArchiveRepository(
            database,
            { ++now },
            { id -> id in setOf("alice", "bob", "new-role") })
        memoryTransfer = RoomMemoryArchiveTransferRepository(
            database,
            archive,
            isCharacterAvailable = { id ->
                settings.settingsFlow.first().resolvedAssistants().any { it.id == id }
            },
            nowProvider = { ++now })
    }

    fun reopenDatabase() {
        database.close(); openDatabase()
    }

    suspend fun reopenSettings() {
        settingsScope.coroutineContext[Job]!!.cancelAndJoin()
        settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = makeSettings()
        settings.settingsFlow.first()
    }

    suspend fun initialize() {
        val assistants = listOf(
            Assistant(id = "alice", name = "甲"),
            Assistant(id = "bob", name = "乙")
        )
        settings.saveAssistants(assistants, "alice")
        settings.settingsFlow.first { it.assistants == assistants && it.selectedAssistantId == "alice" }
    }

    fun recovery(
        store: SettingsStore = settings,
        worldRepository: WorldBookRepository = worlds,
        fileStore: CompanionTransferFileStore = files,
        hook: suspend (String, String) -> Unit = { _, _ -> },
    ) = RoomContextTransferRecoveryRepository(
        database,
        store,
        worldRepository,
        presets,
        memoryTransfer,
        fileStore,
        nowProvider = { ++now },
        phaseHook = hook
    )

    override fun close() {
        database.close()
        runBlocking { settingsScope.coroutineContext[Job]!!.cancelAndJoin() }
        check(root.parentFile == temporaryParent && root.name.startsWith("d6t-"))
        check(root.deleteRecursively())
    }
}
