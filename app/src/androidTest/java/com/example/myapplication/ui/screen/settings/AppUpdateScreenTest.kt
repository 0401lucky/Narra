package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.AppUpdateAvailability
import com.example.myapplication.model.AppUpdateDownloadSnapshot
import com.example.myapplication.model.AppUpdateDownloadStatus
import com.example.myapplication.model.AppUpdateMetadata
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.AppUpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun updateDetails_defaultHiddenAndAccessibleThroughHelp() {
        var checkCalls = 0
        composeRule.setContent {
            ChatAppTheme {
                AppUpdateScreen(
                    uiState = defaultState,
                    onNavigateBack = {},
                    onCheckForUpdates = { checkCalls++ },
                    onStartDownload = {},
                    onInstallUpdate = {},
                    onConsumeMessage = {},
                )
            }
        }

        composeRule.onNodeWithText(defaultState.metadataBaseUrl, substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("检查更新").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, checkCalls) }
        composeRule.onNodeWithContentDescription("更新信息：查看说明")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(defaultState.metadataBaseUrl, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("知道了").performClick()
        composeRule.onNodeWithText(defaultState.metadataBaseUrl, substring = true).assertDoesNotExist()
    }

    @Test
    fun updateProgressAndInstallation_remainAvailableWithoutExposingDownloadId() {
        var downloadCalls = 0
        var installCalls = 0
        var state by mutableStateOf(
            defaultState.copy(
                availability = AppUpdateAvailability.OPTIONAL,
                latestMetadata = AppUpdateMetadata(
                    latestVersionName = "2.2.3-dev",
                    latestVersionCode = 20203,
                    releaseNotes = listOf("改善设置页面的显示与操作"),
                ),
            ),
        )
        composeRule.setContent {
            ChatAppTheme {
                AppUpdateScreen(
                    uiState = state,
                    onNavigateBack = {},
                    onCheckForUpdates = {},
                    onStartDownload = { downloadCalls++ },
                    onInstallUpdate = { installCalls++ },
                    onConsumeMessage = {},
                )
            }
        }

        composeRule.onNodeWithText("立即更新").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(1, downloadCalls)
            state = state.copy(
                downloadSnapshot = AppUpdateDownloadSnapshot(
                    status = AppUpdateDownloadStatus.RUNNING,
                    downloadId = 42L,
                    bytesDownloaded = 50L,
                    totalBytes = 100L,
                ),
            )
        }
        composeRule.onNodeWithText("正在下载，已完成 50%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("下载中").assertIsNotEnabled()
        composeRule.onNodeWithText("下载任务 #42").assertDoesNotExist()
        composeRule.runOnIdle {
            state = state.copy(
                downloadSnapshot = state.downloadSnapshot.copy(
                    status = AppUpdateDownloadStatus.FAILED,
                    reason = "网络连接中断，请重试",
                ),
            )
        }
        composeRule.onNodeWithText("网络连接中断，请重试").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("立即更新").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(2, downloadCalls) }
        composeRule.runOnIdle {
            state = state.copy(downloadSnapshot = state.downloadSnapshot.copy(status = AppUpdateDownloadStatus.DOWNLOADED))
        }
        composeRule.onNodeWithText("安装更新").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, installCalls) }
    }

    private val defaultState = AppUpdateUiState(
        currentVersionName = "2.2.2-dev",
        currentVersionCode = 20202,
        channel = "dev",
        metadataBaseUrl = "https://updates.example.test/dev.json",
    )
}
