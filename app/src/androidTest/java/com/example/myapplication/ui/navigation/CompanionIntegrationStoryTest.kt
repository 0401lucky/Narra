package com.example.myapplication.ui.navigation

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.security.NetworkSecurityPolicy
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.compliance.CompliancePolicy
import com.example.myapplication.data.local.AppSettingsStore
import com.example.myapplication.data.local.ComplianceConsentStore
import com.example.myapplication.data.local.SettingsSecureValueStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.di.AppGraph
import com.example.myapplication.di.AppGraphStorageOverrides
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.ui.screen.roleplay.assertCompanionDialogActionAboveIme
import com.example.myapplication.viewmodel.AppUpdateViewModel
import com.example.myapplication.viewmodel.ComplianceViewModel
import com.example.myapplication.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.util.UUID
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** 真实 AppGraph、AppNavHost 与主壳入口；仅持久存储和 HTTP 端点为隔离合成装配。 */
@RunWith(AndroidJUnit4::class)
class CompanionIntegrationStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var application: Application
    private lateinit var graph: AppGraph
    private lateinit var database: ChatDatabase
    private lateinit var server: MockWebServer
    private lateinit var testDirectory: File
    private lateinit var databaseName: String
    private lateinit var navigation: NavHostController
    private val storageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var originalSession: RoleplaySession

    @Before
    fun prepare() = runBlocking<Unit> {
        application =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val token = UUID.randomUUID().toString().take(8)
        testDirectory = File(application.cacheDir, "d6-story-$token").apply { mkdirs() }
        databaseName = "d6-story-$token.db"
        database = Room.databaseBuilder(application, ChatDatabase::class.java, databaseName)
            .addMigrations(*ChatDatabase.ALL_MIGRATIONS).build()
        assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("localhost"))
        assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1"))
        assertFalse(
            NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("example.invalid")
        )
        server = MockWebServer().also { it.start(InetAddress.getByName("127.0.0.1"), 0) }
        check(server.url("/v1/").host in setOf("localhost", "127.0.0.1"))
        val preferences = PreferenceDataStoreFactory.create(scope = storageScope) {
            File(testDirectory, "settings.preferences_pb")
        }
        val settings =
            AppSettingsStore(preferences, SyntheticSecureStore(), storageScope, Dispatchers.IO)
        settings.saveAssistants(
            listOf(
                Assistant(
                    id = ALICE,
                    name = "D6 合成角色",
                    greeting = "",
                    systemPrompt = "甲角色事实哨兵",
                    memoryEnabled = false
                ),
                Assistant(id = BOB, name = "D6 其它角色", greeting = "", memoryEnabled = false),
            ),
            ALICE,
        )
        val provider = ProviderSettings(
            id = "d6-synthetic", baseUrl = server.url("/v1/").toString(),
            apiKey = "synthetic-d6-key", selectedModel = "synthetic-d6-chat",
            openAiTextApiMode = OpenAiTextApiMode.CHAT_COMPLETIONS,
            apiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        )
        settings.saveProviderSettings(listOf(provider), provider.id)
        settings.saveContextLogSettings(enabled = false, capacity = DEFAULT_CONTEXT_LOG_CAPACITY)
        val consent =
            ComplianceConsentStore(PreferenceDataStoreFactory.create(scope = storageScope) {
                File(testDirectory, "consent.preferences_pb")
            })
        consent.accept(CompliancePolicy.CURRENT_VERSION, System.currentTimeMillis())
        graph = AppGraph(application, AppGraphStorageOverrides(database, settings, consent))
        graph.roleplayRepository.upsertScenario(
            RoleplayScenario(
                id = SCENE,
                title = TITLE,
                assistantId = ALICE,
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
                memoryPurpose = MemoryPurpose.CONTINUOUS,
                userPersonaOverride = "D6 身份甲",
                openingNarration = "",
                enableTimeAwareness = false,
            ),
        )
        originalSession = graph.roleplayRepository.startScenario(SCENE).session
        val start = System.currentTimeMillis() - 130_000L
        graph.conversationRepository.upsertMessages(
            originalSession.conversationId,
            (0..120).map { index ->
                ChatMessage(
                    "d6-old-$index", originalSession.conversationId, MessageRole.USER,
                    "原线上经历哨兵 $index", createdAt = start + index * 1_000L,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                )
            },
            "synthetic-d6-chat",
        )
        graph.roleplayRepository.upsertScenario(
            RoleplayScenario(
                id = "d6-bob-scene", title = "其它角色的场", assistantId = BOB,
                memoryPurpose = MemoryPurpose.CONTINUOUS, userPersonaOverride = "D6 身份甲"
            ),
        )
        val bobSession = graph.roleplayRepository.startScenario("d6-bob-scene").session
        graph.conversationRepository.upsertMessages(
            bobSession.conversationId,
            listOf(
                ChatMessage(
                    "d6-foreign",
                    bobSession.conversationId,
                    MessageRole.USER,
                    FOREIGN,
                    createdAt = start
                )
            ),
            "synthetic-d6-chat"
        )
    }

    @After
    fun close() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        storageScope.cancel()
        if (::database.isInitialized) database.close()
        if (::databaseName.isInitialized) {
            check(databaseName.startsWith("d6-story-") && !databaseName.contains('/'))
            application.deleteDatabase(databaseName)
        }
        if (::testDirectory.isInitialized) {
            check(
                testDirectory.canonicalFile.toPath()
                    .startsWith(application.cacheDir.canonicalFile.toPath())
            )
            testDirectory.deleteRecursively()
        }
        if (::server.isInitialized) server.shutdown()
    }

    @Test
    fun productionEntry_completeStoryPreservesRunRawAndModelAuthorization() {
        enqueueReply("<narration>合成开场。</narration><dialogue>一起坐一会儿。</dialogue>")
        enqueueReply("<dialogue>合成输入的回应。</dialogue>")
        enqueueReply("<narration>安静片刻。</narration><dialogue>合成继续的回应。</dialogue>")
        enqueueReply("<dialogue sprite=\"normal\">合成重生成的回应。</dialogue>")
        renderProductionNavigation()
        openOriginalOnline()
        assertEquals(0, server.requestCount)

        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("开始见面").performClick()
        shown("companion_approach")
        assertEquals(0, server.requestCount)
        compose.onNodeWithTag("companion_approach").performClick()
        shown("companion_preview")
        compose.onNodeWithTag("companion_accept").assertIsDisplayed()
        captureWindow("opening-preview")
        assertEquals(1, server.requestCount)
        val beforeAccept =
            runBlocking { graph.conversationRepository.listMessages(originalSession.conversationId) }
        assertFalse(beforeAccept.any { it.content.contains("合成开场") })
        compose.onNodeWithTag("companion_accept").performClick()
        shown("companion_continue")
        val accepted =
            runBlocking { graph.conversationRepository.listMessages(originalSession.conversationId) }
        assertEquals(1, accepted.count { it.content.contains("合成开场") })

        enabled("roleplay_input_editor")
        compose.onNodeWithTag("roleplay_input_editor").performTextReplacement("只坐一会儿")
        compose.onNodeWithTag("roleplay_input_editor").performImeAction()
        compose.waitUntil(10_000) {
            runBlocking {
                graph.conversationRepository.listMessages(
                    originalSession.conversationId
                )
            }.any { it.content.contains("合成输入的回应") }
        }
        enabled("companion_continue")
        compose.onNodeWithTag("companion_continue").performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                graph.conversationRepository.listMessages(
                    originalSession.conversationId
                )
            }.any { it.content.contains("合成继续的回应") }
        }
        val beforeReroll =
            runBlocking { graph.conversationRepository.listMessages(originalSession.conversationId) }.single {
                it.content.contains("合成继续的回应")
            }
        enabled("companion_regenerate")
        compose.onNodeWithTag("companion_regenerate").performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                graph.conversationRepository.listMessages(
                    originalSession.conversationId
                )
            }.any { it.content.contains("合成重生成的回应") }
        }
        val rerolled =
            runBlocking { graph.conversationRepository.listMessages(originalSession.conversationId) }.single { it.id == beforeReroll.id }
        assertEquals("<dialogue sprite=\"normal\">合成重生成的回应。</dialogue>", rerolled.content)
        assertEquals(beforeReroll.createdAt, rerolled.createdAt)
        assertTrue(rerolled.memoryRevision > beforeReroll.memoryRevision)
        assertEquals(4, server.requestCount)

        compose.onNodeWithTag("companion_open_reading").performClick()
        shown("companion_reader")
        compose.onNodeWithTag("companion_reader_history").performClick()
        shown("companion_history")
        historyCountLoaded(50)
        captureWindow("history-first-window")
        assertOriginalRun()
        val reading =
            runBlocking { requireNotNull(graph.companionReadingRepository.authorize(SCENE, ALICE)) }
        val all = runBlocking { graph.companionReadingRepository.readAll(reading) }
        assertTrue(all.size > 121)
        assertFalse(all.any { it.message.content.contains(FOREIGN) })

        reveal("companion_history_edit_one_${rerolled.id}")
        compose.onNodeWithTag("companion_history_edit_one_${rerolled.id}").performClick()
        shown("companion_history_edit_${rerolled.id}")
        captureWindow("history-original-editor")
        val editedRaw = "<dialogue>人工编辑的完整舞台原文 &amp; 👩‍💻</dialogue>"
        compose.onNodeWithTag("companion_history_edit_${rerolled.id}").performClick()
            .performTextReplacement(editedRaw)
        assertCompanionDialogActionAboveIme(compose, compose.activity, "保存编辑")
        captureWindow("history-edited-with-ime")
        compose.onNodeWithText("保存编辑").performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                graph.conversationRepository.listMessages(
                    originalSession.conversationId
                )
            }.any { it.id == rerolled.id && it.content.contains("人工编辑") }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("companion_history_edit_${rerolled.id}").fetchSemanticsNodes()
                .isEmpty()
        }
        val edited =
            runBlocking { graph.conversationRepository.listMessages(originalSession.conversationId) }.single { it.id == rerolled.id }
        assertEquals(editedRaw, edited.content)
        assertTrue(edited.memoryRevision > rerolled.memoryRevision)

        reveal("companion_history_export")
        enabled("companion_history_export")
        captureWindow("history-ready-after-edit")
        compose.onNodeWithTag("companion_history_export").performClick()
        compose.waitUntil(10_000) { !compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4")
            .use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        compose.waitUntil(10_000) { compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        assertTrue(
            compose.onAllNodesWithTag("companion_history_notice").fetchSemanticsNodes().none {
                it.config.toString().contains("已导出")
            })

        reveal("companion_history_select_${edited.id}")
        enabled("companion_history_select_${edited.id}")
        compose.onNodeWithTag("companion_history_select_${edited.id}").performClick()
        reveal("companion_history_delete_selected")
        enabled("companion_history_delete_selected")
        compose.onNodeWithTag("companion_history_delete_selected").performClick()
        compose.onNodeWithText("删除原文").performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                graph.conversationRepository.listMessages(
                    originalSession.conversationId
                )
            }.none { it.id == edited.id }
        }
        compose.onNodeWithText("声音库").performClick()
        shown("companion_voice_screen")
        captureWindow("voice-library")
        compose.onNodeWithTag("companion_voice_back").performClick()
        shown("companion_history")
        compose.onNodeWithText("本地装扮包").performClick()
        shown("companion_appearance_transfer")
        enabled("companion_appearance_export")
        captureWindow("appearance-transfer")
        compose.onNodeWithTag("companion_appearance_transfer_close").performClick()
        shown("companion_history")
        compose.onNodeWithTag("companion_history_close").performClick()
        shown("companion_reader")
        compose.onNodeWithTag("companion_reader_close").performClick()
        shown("companion_continue")
        assertEquals(4, server.requestCount)
        assertOriginalRun()

        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(10_000) { runBlocking { graph.roleplayRepository.getScenario(SCENE) }?.interactionMode == RoleplayInteractionMode.ONLINE_PHONE }
        shown("roleplay_online_input")
        captureWindow("returned-online")
        assertOriginalRun()
        enqueueReply("[\"线上格式恢复的合成回应\"]")
        compose.onNodeWithTag("roleplay_input_editor").performTextReplacement("线上继续聊")
        compose.onNodeWithTag("roleplay_input_editor").performImeAction()
        compose.waitUntil(10_000) {
            runBlocking {
                graph.conversationRepository.listMessages(
                    originalSession.conversationId
                )
            }.any { it.content.contains("线上格式恢复的合成回应") }
        }
        val last =
            runBlocking { graph.conversationRepository.listMessages(originalSession.conversationId) }.last { it.role == MessageRole.ASSISTANT }
        assertEquals(RoleplayInteractionMode.ONLINE_PHONE, last.roleplayInteractionMode)
        assertEquals(5, server.requestCount)
        val requests = (1..5).map { requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        assertTrue(requests.all { it.path == "/v1/chat/completions" })
        val lastBody = requests.last().body.readUtf8()
        assertTrue(lastBody.contains("线上继续聊"))
        assertTrue(lastBody.contains("合成开场"))
        assertFalse(lastBody.contains(FOREIGN))
        assertFalse(lastBody.contains("合成重生成的回应"))
        assertOriginalRun()
    }

    @Test
    fun productionRoleFilter_emptyResultCanClearAndReturnWithoutRequests() {
        renderProductionNavigation()
        shown("phone_bottom_bar")
        compose.onNodeWithTag("phone_tab_Contacts").performClick()
        compose.onNodeWithText("新的角色").assertIsDisplayed()
        compose.onNodeWithTag("phone_tab_Messages").performClick()
        compose.onNodeWithContentDescription("新增").performClick()
        compose.onNodeWithText("新建会话").performClick()
        shown("new_chat_role_filter")
        compose.onNodeWithTag("new_chat_role_filter")
            .performTextReplacement("没有这个名称或标签-合成哨兵")
        compose.onNodeWithTag("new_chat_role_empty").assertIsDisplayed()
        captureWindow("role-filter-empty")
        compose.onNodeWithText("清除筛选").performClick()
        compose.onNodeWithText("D6 合成角色").assertIsDisplayed()
        compose.onNodeWithTag("new_chat_role_filter").performTextReplacement("再次空筛选-合成哨兵")
        compose.onNodeWithTag("new_chat_empty_back").performClick()
        shown("phone_bottom_bar")
        assertEquals(0, server.requestCount)
        assertOriginalRun()
    }

    @Test
    @SuppressLint("NewApi")
    fun productionDocuments_queuedSafExportsAllRawAndShowsNewFailureWithoutLosingOriginals() {
        val originalMessages =
            runBlocking { graph.conversationRepository.listMessages(originalSession.conversationId) }
        val raw =
            "<literal>保留标签 &amp;、连续空白  、👩‍💻 与原换行。\r\n".repeat(24) + "完整原文末尾事实。</literal>"
        val firstFocusId = "d6-focus/中文?first=甲&"
        val secondFocusId = "d6-focus/中文?second=乙#"
        runBlocking {
            graph.conversationRepository.upsertMessages(
                originalSession.conversationId,
                listOf(
                    originalMessages.first().copy(content = raw),
                    ChatMessage(
                        firstFocusId,
                        originalSession.conversationId,
                        MessageRole.USER,
                        "第一条定位原文",
                        createdAt = originalMessages.first().createdAt - 2_000,
                        roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE
                    ),
                    ChatMessage(
                        secondFocusId,
                        originalSession.conversationId,
                        MessageRole.USER,
                        "第二条定位原文",
                        createdAt = originalMessages.last().createdAt + 1_000,
                        roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE
                    )
                ), "synthetic-d6-chat"
            )
        }
        val lateFile = File(testDirectory, "late-result.txt").apply { writeText("旧请求不得覆盖") }
        val outputFile = File(testDirectory, "current-result.txt")
        val provider = SyntheticDocumentProvider(lateFile, outputFile).apply {
            attachInfo(
                application,
                ProviderInfo().apply { authority = DOCUMENT_AUTHORITY; exported = false })
        }
        val resolver = ContentResolver.wrap(provider)
        graph = AppGraph(
            DocumentApplication(application, resolver),
            AppGraphStorageOverrides(database, graph.settingsStore, graph.complianceConsentStore)
        )
        val documents = DocumentResultRegistry()
        renderProductionNavigation(documents)
        openOriginalOnline()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("开始见面").performClick()
        shown("companion_approach")
        compose.onNodeWithText("查看记录").performClick()
        shown("companion_history")
        historyCountLoaded(50)
        reveal("companion_history_export")
        enabled("companion_history_export")
        compose.onNodeWithTag("companion_history_export").performClick()
        compose.waitUntil(10_000) { documents.launches.size == 1 }

        reveal("companion_history_legacy_filter")
        compose.onNodeWithTag("companion_history_legacy_filter").performClick()
        reveal("companion_history_export")
        enabled("companion_history_export")
        reveal("companion_history_export")
        compose.onNodeWithTag("companion_history_export").performClick()
        compose.runOnIdle { assertEquals(1, documents.launches.size) }
        compose.runOnIdle { documents.complete(0, Uri.parse("content://$DOCUMENT_AUTHORITY/late")) }
        compose.waitUntil(10_000) { documents.launches.size == 2 }
        assertEquals(0, provider.opens.get())
        assertEquals("旧请求不得覆盖", lateFile.readText())
        compose.runOnIdle {
            documents.complete(
                1,
                Uri.parse("content://$DOCUMENT_AUTHORITY/current")
            )
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("companion_history_notice").fetchSemanticsNodes().any {
                it.config[SemanticsProperties.Text].any { text -> text.text == "已导出 123 条原文。" }
            }
        }
        val bytes = outputFile.readBytes()
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            bytes.copyOfRange(0, 3)
        )
        val exported = bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        assertTrue(exported.contains(raw))
        val expected =
            runBlocking { graph.conversationRepository.listMessages(originalSession.conversationId) }
        assertEquals(123, expected.size)
        expected.forEach {
            assertTrue(
                "TXT 必须包含未载入原文 ${it.id}",
                exported.contains(it.content)
            )
        }
        assertFalse(exported.contains(FOREIGN))
        assertEquals(1, provider.opens.get())

        compose.onNodeWithTag("companion_history_export").performClick()
        compose.waitUntil(10_000) { documents.launches.size == 3 }
        compose.runOnIdle {
            documents.complete(
                2,
                Uri.parse("content://$DOCUMENT_AUTHORITY/denied")
            )
        }
        shown("companion_history_error")
        assertTrue(
            compose.onAllNodesWithTag("companion_history_notice").fetchSemanticsNodes().isEmpty()
        )
        assertEquals(
            expected,
            runBlocking { graph.conversationRepository.listMessages(originalSession.conversationId) })
        captureWindow("history-export-io-failure")
        compose.runOnIdle {
            navigation.navigate(
                AppRoutes.roleplayCompanionReading(
                    SCENE,
                    ALICE,
                    firstFocusId
                )
            ) { launchSingleTop = true }
        }
        shown("companion_reader")
        shownText("第一条定位原文")
        compose.runOnIdle {
            navigation.navigate(
                AppRoutes.roleplayCompanionReading(
                    SCENE,
                    ALICE,
                    secondFocusId
                )
            ) { launchSingleTop = true }
        }
        shown("companion_reader")
        shownText("第二条定位原文")
        compose.onNodeWithTag("companion_reader_history").performClick()
        shown("companion_history")
        compose.onNodeWithText("本地装扮包").performClick()
        shown("companion_appearance_transfer")
        enabled("companion_appearance_export")
        compose.onNodeWithTag("companion_appearance_export").performClick()
        compose.waitUntil(10_000) { documents.launches.size == 4 }
        compose.runOnIdle {
            documents.complete(
                3,
                Uri.parse("content://$DOCUMENT_AUTHORITY/appearance")
            )
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("companion_appearance_message").fetchSemanticsNodes().any {
                it.config[SemanticsProperties.Text].any { text -> text.text == "装扮包已导出" }
            }
        }
        val packageFile = File(testDirectory, "appearance-result.json")
        assertEquals(
            1,
            AppJson.gson.fromJson(
                packageFile.readText(),
                CompanionAppearancePackage::class.java
            ).version
        )
        val settingsBeforeFailedImport = runBlocking { graph.settingsStore.settingsFlow.first() }
        enabled("companion_appearance_import")
        compose.onNodeWithTag("companion_appearance_import").performClick()
        compose.waitUntil(10_000) { documents.launches.size == 5 }
        compose.runOnIdle {
            documents.complete(
                4,
                Uri.parse("content://$DOCUMENT_AUTHORITY/invalid")
            )
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("companion_appearance_message").fetchSemanticsNodes().any {
                it.config[SemanticsProperties.Text].any { text -> text.text.isNotBlank() && text.text != "装扮包已导出" }
            }
        }
        compose.onNodeWithTag("companion_appearance_preview").assertDoesNotExist()
        assertEquals(
            settingsBeforeFailedImport,
            runBlocking { graph.settingsStore.settingsFlow.first() })
        assertEquals(
            expected,
            runBlocking { graph.conversationRepository.listMessages(originalSession.conversationId) })
        assertEquals(0, server.requestCount)
        assertOriginalRun()
        captureWindow("appearance-invalid-import-after-export")
    }

    private fun renderProductionNavigation(registry: ActivityResultRegistry? = null) {
        compose.setContent {
            navigation = rememberNavController()
            val settings: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(
                    graph.aiSettingsRepository,
                    graph.aiSettingsEditor,
                    graph.aiModelCatalogRepository
                )
            )
            val compliance: ComplianceViewModel =
                viewModel(factory = ComplianceViewModel.factory(graph.complianceConsentStore))
            val updates: AppUpdateViewModel = viewModel(
                factory = AppUpdateViewModel.factory(
                    graph.appUpdateRepository, graph.appUpdateDownloadController,
                    AppUpdateEnvironment(application.packageName, "dev", "synthetic-d6", 1, "")
                )
            )
            val registryOwner = remember(registry) {
                registry?.let { supplied ->
                    object : ActivityResultRegistryOwner {
                        override val activityResultRegistry = supplied
                    }
                }
            } ?: checkNotNull(LocalActivityResultRegistryOwner.current) {
                "真实导航宿主必须提供 ActivityResultRegistryOwner"
            }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                ChatAppTheme {
                    AppNavHost(
                        graph,
                        navigation,
                        settings,
                        updates,
                        compliance,
                        AppRoutes.ROLEPLAY_GRAPH
                    )
                }
            }
        }
        shown("phone_bottom_bar")
    }

    private fun openOriginalOnline() {
        val rowTag = "phone_swipe_row_$SCENE"
        shown(rowTag)
        compose.onNodeWithTag(rowTag).performClick()
        shown("roleplay_online_input")
    }

    private fun assertOriginalRun() {
        val session =
            runBlocking { requireNotNull(graph.roleplayRepository.getSessionByScenario(SCENE)) }
        assertEquals(originalSession.id, session.id)
        assertEquals(originalSession.conversationId, session.conversationId)
        assertEquals(originalSession.runId, session.runId)
    }

    private fun shown(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun shownText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun historyCountLoaded(count: Int) {
        val expected = "已载入 $count 条"
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("companion_history_count").fetchSemanticsNodes().any {
                it.config[SemanticsProperties.Text].any { text -> text.text.contains(expected) }
            }
        }
        compose.onNodeWithTag("companion_history_count")
            .assertTextContains(expected, substring = true)
    }

    private fun enabled(tag: String) {
        compose.waitUntil(10_000) {
            val nodes = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
            nodes.size == 1 && !nodes.single().config.contains(SemanticsProperties.Disabled)
        }
    }

    private fun reveal(tag: String) {
        compose.onNodeWithTag("companion_history_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun enqueueReply(text: String) {
        val delta = AppJson.gson.toJson(
            mapOf(
                "choices" to listOf(
                    mapOf(
                        "index" to 0,
                        "delta" to mapOf("content" to text)
                    )
                )
            )
        )
        val done = AppJson.gson.toJson(
            mapOf(
                "choices" to listOf(
                    mapOf(
                        "index" to 0,
                        "delta" to emptyMap<String, String>(),
                        "finish_reason" to "stop"
                    )
                )
            )
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody("data: $delta\n\ndata: $done\n\ndata: [DONE]\n\n")
        )
    }

    private fun captureWindow(name: String) {
        compose.waitForIdle()
        val bitmap =
            requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val directory = File(
                application.filesDir,
                "d6-integration-evidence/${databaseName.removeSuffix(".db")}"
            )
            check(directory.mkdirs() || directory.isDirectory)
            FileOutputStream(File(directory, "$name.png")).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            File(
                directory,
                "$name.txt"
            ).writeText("case=$name\nroute=${navigation.currentBackStackEntry?.destination?.route}\nwidth=${bitmap.width}\nheight=${bitmap.height}\n")
        } finally {
            bitmap.recycle()
        }
    }

    private class SyntheticSecureStore : SettingsSecureValueStore {
        private val values = mutableMapOf<String, String>()
        private val maps = mutableMapOf<String, Map<String, String>>()
        override fun getString(key: String) = values[key].orEmpty()
        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun getStringMap(key: String) = maps[key].orEmpty()
        override fun putStringMap(key: String, values: Map<String, String>) {
            maps[key] = values.toMap()
        }
    }

    private class DocumentResultRegistry : ActivityResultRegistry() {
        val launches = CopyOnWriteArrayList<Int>()
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            when (contract) {
                is ActivityResultContracts.CreateDocument -> check(
                    input is String && (input.endsWith(
                        ".txt"
                    ) || input.endsWith(".json"))
                )

                is ActivityResultContracts.OpenDocument -> check(
                    input is Array<*> && input.toSet() == setOf(
                        "application/json",
                        "text/plain"
                    )
                )

                else -> error("不应打开其它合成文档入口")
            }
            launches += requestCode
        }

        fun complete(index: Int, uri: Uri) {
            check(dispatchResult(launches[index], uri))
        }
    }

    private class DocumentApplication(base: Application, private val documents: ContentResolver) :
        Application() {
        init {
            attachBaseContext(base)
        }

        override fun getContentResolver(): ContentResolver = documents
    }

    private class SyntheticDocumentProvider(
        private val lateFile: File,
        private val outputFile: File
    ) : ContentProvider() {
        private val packageFile = File(outputFile.parentFile, "appearance-result.json")
        private val invalidPackageFile = File(
            outputFile.parentFile,
            "invalid-appearance.json"
        ).apply { writeText("{\"version\":99}") }
        val opens = AtomicInteger()
        override fun onCreate() = true
        override fun getType(uri: Uri) = "text/plain"
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("不应插入合成文档")
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = error("不应更新合成文档")

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            error("不应删除合成文档")

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val target = when (uri.lastPathSegment) {
                "late" -> lateFile
                "current" -> outputFile
                "appearance" -> packageFile
                "invalid" -> invalidPackageFile
                else -> throw FileNotFoundException("合成文档写入失败")
            }
            opens.incrementAndGet()
            return ParcelFileDescriptor.open(target, ParcelFileDescriptor.parseMode(mode))
        }
    }

    companion object {
        private const val SCENE = "d6-online-scene"
        private const val TITLE = "D6 合成线上会话"
        private const val ALICE = "d6-alice"
        private const val BOB = "d6-bob"
        private const val FOREIGN = "其它角色的私人原文不得进入"
        private const val DOCUMENT_AUTHORITY = "d6.synthetic.documents"
    }
}
