package com.example.myapplication.ui.screen.settings

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.local.AppUpdateStateStore
import com.example.myapplication.data.repository.AppUpdateRepository
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.model.*
import com.example.myapplication.system.update.AppUpdateDownloadController
import com.example.myapplication.testutil.TestOpenAiCompatibleApi
import com.example.myapplication.testutil.createTestAiServices
import com.example.myapplication.ui.navigation.AppRoutes
import com.example.myapplication.ui.navigation.registerSettingsProviderRoutes
import com.example.myapplication.ui.screenshot.ProviderSettingsRenderTest
import com.example.myapplication.viewmodel.AppUpdateViewModel
import com.example.myapplication.viewmodel.SettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/** 使用真实目的地绑定验证返回顺序；更新与 Provider API 都是隔离的本地 Fake。 */
class ProviderNavigationTest : ProviderSettingsRenderTest() {
    @Test
    fun listBack_startsSaveThenPopsImmediately_evenWhenWriteLaterFails() =
        verifyBack(detail = false)

    @Test
    fun detailBack_startsSaveThenPopsImmediately_evenWhenWriteLaterFails() =
        verifyBack(detail = true)

    private fun verifyBack(detail: Boolean) {
        val api = object : TestOpenAiCompatibleApi() {
            override suspend fun listModels(): Response<ModelsResponse> =
                Response.success(ModelsResponse())
        }
        val services =
            createTestAiServices(fixtureState().savedSettings, apiServiceProvider = { _, _ -> api })
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val editor = object : AiSettingsEditor by services.settingsEditor {
            override suspend fun saveProviderSettings(
                providers: List<ProviderSettings>,
                selectedProviderId: String
            ) {
                writes++
                gate.await()
                error("合成返回保存失败")
            }
        }
        val viewModel =
            SettingsViewModel(services.settingsRepository, editor, services.modelCatalogRepository)
        val updates = isolatedUpdateViewModel()
        lateinit var navigation: NavHostController
        drainMainLooper()
        renderProvider(content = {
            navigation = rememberNavController()
            NavHost(navController = navigation, startDestination = "p11-origin") {
                composable("p11-origin") { Text("合成入口") }
                registerSettingsProviderRoutes(navigation, viewModel, updates)
            }
        })
        composeRule.runOnIdle {
            viewModel.updateProviderName(ProviderId, "合成路由草稿")
            navigation.navigate(if (detail) AppRoutes.settingsProviderDetail(ProviderId) else AppRoutes.SETTINGS_PROVIDERS)
        }
        drainMainLooper()
        composeRule.waitForIdle()
        pageControl(hasContentDescription("返回")).performTouchInput { click() }
        drainMainLooper()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("合成入口").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("p11-origin", navigation.currentDestination?.route)
            assertEquals(1, writes)
            assertTrue(viewModel.uiState.value.isSaving)
            gate.complete(Unit)
        }
        drainMainLooper()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("p11-origin", navigation.currentDestination?.route)
            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.hasDraftChanges())
            assertEquals("合成路由草稿", viewModel.uiState.value.providers.first().name)
            assertTrue(viewModel.uiState.value.message.orEmpty().contains("合成返回保存失败"))
        }
    }

    private fun isolatedUpdateViewModel(): AppUpdateViewModel {
        val store = object : AppUpdateStateStore {
            override val stateFlow = MutableStateFlow(AppUpdateLocalState())
            override suspend fun saveLastCheckAt(value: Long) {
                stateFlow.value = stateFlow.value.copy(lastCheckAt = value)
            }

            override suspend fun saveCachedMetadataJson(value: String) {
                stateFlow.value = stateFlow.value.copy(cachedMetadataJson = value)
            }

            override suspend fun clearCachedMetadata() {
                stateFlow.value = stateFlow.value.copy(cachedMetadataJson = "")
            }

            override suspend fun saveActiveDownloadId(value: Long) {
                stateFlow.value = stateFlow.value.copy(activeDownloadId = value)
            }

            override suspend fun clearActiveDownloadId() {
                stateFlow.value = stateFlow.value.copy(activeDownloadId = 0L)
            }
        }
        return AppUpdateViewModel(
            repository = AppUpdateRepository(
                store,
                metadataFetcher = { error("不应读取远端更新清单") }),
            downloadController = object : AppUpdateDownloadController {
                override fun enqueueDownload(metadata: AppUpdateMetadata): Long = error("不应下载")
                override fun queryDownload(downloadId: Long): AppUpdateDownloadSnapshot =
                    error("不应访问下载状态")

                override suspend fun installDownloadedPackage(
                    downloadId: Long,
                    metadata: AppUpdateMetadata
                ): AppUpdateInstallResult = error("不应安装")
            },
            environment = AppUpdateEnvironment(
                appId = "fixture.narra",
                channel = "dev",
                versionName = "fixture",
                versionCode = 1,
                metadataBaseUrl = ""
            ),
        )
    }
}
