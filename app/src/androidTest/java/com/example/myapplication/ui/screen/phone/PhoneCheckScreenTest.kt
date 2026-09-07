package com.example.myapplication.ui.screen.phone

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.PhoneGalleryEntry
import com.example.myapplication.model.PhoneMessageItem
import com.example.myapplication.model.PhoneMessageThread
import com.example.myapplication.model.PhoneNoteEntry
import com.example.myapplication.model.PhoneSearchDetail
import com.example.myapplication.model.PhoneSearchEntry
import com.example.myapplication.model.PhoneShoppingEntry
import com.example.myapplication.model.PhoneSocialComment
import com.example.myapplication.model.PhoneSocialPost
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotSection
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.PhoneCheckUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneCheckScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun refresh_largeFont_cancelKeepsDataAndConfirmUsesOnlySelectedSections() {
        val refreshes = mutableListOf<Set<PhoneSnapshotSection>>()
        showPhone(fontScale = 1.6f, onRefresh = { refreshes += it })

        composeRule.onNodeWithContentDescription("刷新手机内容").performClick()
        composeRule.onNodeWithTag("phone_refresh_social_posts").performScrollTo()
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        captureReviewImage("phone-refresh-large-font", dialog = true)
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertEquals(0, refreshes.size) }

        composeRule.onNodeWithContentDescription("刷新手机内容").performClick()
        PhoneSnapshotSection.entries.forEach { section ->
            composeRule.onNodeWithTag("phone_refresh_" + section.storageValue)
                .performScrollTo().assertIsOn().performClick()
        }
        composeRule.onNodeWithText("确认刷新").assertIsNotEnabled()
        composeRule.onNodeWithTag("phone_refresh_notes").performScrollTo().performClick()
        composeRule.onNodeWithText("确认刷新").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(setOf(PhoneSnapshotSection.NOTES)), refreshes)
        }
    }

    @Test
    fun categories_largeFont_openAllDetailsAndBackKeepsPhoneNavigation() {
        var backCalls = 0
        showPhone(fontScale = 1.6f, dark = { true }, onBack = { backCalls++ })
        val detailCases = listOf(
            Triple(PhoneSnapshotSection.MESSAGES, "季望", "等你一起散步"),
            Triple(PhoneSnapshotSection.NOTES, "周末出行的备忘录", "记得带上相机和雨伞"),
            Triple(PhoneSnapshotSection.GALLERY, "窗边的晴天", "阳光停在杯沿"),
            Triple(PhoneSnapshotSection.SHOPPING, "一束白色小花", "准备放在餐桌上"),
            Triple(PhoneSnapshotSection.SEARCH, "附近安静的书店", "沿着河边走十分钟"),
            Triple(PhoneSnapshotSection.SOCIAL_POSTS, "今天的风很温柔", "下次也想一起去"),
        )
        detailCases.forEach { (section, entryText, detailText) ->
            selectSection(section)
            composeRule.onNodeWithTag("phone_list").performScrollToNode(hasText(entryText))
            composeRule.onNodeWithText(entryText).performClick()
            composeRule.onNodeWithTag("phone_list").performScrollToNode(hasText(detailText, substring = true))
            composeRule.onNodeWithText(detailText, substring = true).assertIsDisplayed()
            if (section == PhoneSnapshotSection.SOCIAL_POSTS) {
                captureReviewImage("phone-social-dark-large-font")
            }
            composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            composeRule.onNodeWithTag("phone_tab_" + section.storageValue).assertIsSelected()
        }
        composeRule.runOnIdle { assertEquals(0, backCalls) }
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test
    fun search_failureRetainsRecordAndRetryLoadsDetail() {
        val search = testSnapshot.searchHistory.single().copy(detail = null)
        var state by mutableStateOf(
            PhoneCheckUiState(
                isLoading = false,
                ownerName = "季望",
                snapshot = PhoneSnapshot(conversationId = "phone-test", searchHistory = listOf(search)),
            ),
        )
        val loadedIds = mutableListOf<String>()
        showPhone(
            state = { state },
            onLoadSearch = {
                loadedIds += it
                state = state.copy(loadingSearchEntryId = it)
            },
        )
        composeRule.onNodeWithText(search.query).performClick()
        composeRule.onNodeWithText("正在生成搜索详情").assertIsDisplayed()
        composeRule.runOnIdle {
            state = state.copy(loadingSearchEntryId = "", errorMessage = "网络暂不可用")
        }
        composeRule.onNodeWithText("搜索详情尚未加载").assertIsDisplayed()
        composeRule.onNodeWithText("重试生成详情").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(search.id, search.id), loadedIds)
            state = state.copy(
                loadingSearchEntryId = "",
                errorMessage = null,
                snapshot = state.snapshot!!.withSearchDetail(
                    search.id,
                    PhoneSearchDetail("河边书店", "适合慢慢阅读", "沿着河边走十分钟"),
                    updatedAt = 1L,
                ),
            )
        }
        composeRule.onNodeWithText("沿着河边走十分钟", substring = true).assertIsDisplayed()
        captureReviewImage("phone-search-recovered")
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText(search.query).assertIsDisplayed()
    }

    @Test
    fun emptyAndGeneratingStates_keepHelpAndPreventDuplicateGeneration() {
        var state by mutableStateOf(PhoneCheckUiState(isLoading = true))
        var generateCalls = 0
        showPhone(
            state = { state },
            fontScale = 1.6f,
            onGenerate = {
                generateCalls++
                state = state.copy(isGenerating = true, generationStatusText = "正在生成消息 1 / 6")
            },
        )
        composeRule.onNodeWithText("正在读取手机内容").assertIsDisplayed()
        composeRule.runOnIdle { state = state.copy(isLoading = false) }
        composeRule.onNodeWithTag("phone_generate").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("phone_generate").assertIsNotEnabled()
        composeRule.onNodeWithText("正在生成消息 1 / 6").assertIsDisplayed()
        captureReviewImage("phone-generating-large-font")
        composeRule.onNodeWithContentDescription("手机内容：查看说明").performClick()
        composeRule.onNodeWithText("知道了").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            state = state.copy(isGenerating = false, generationStatusText = "", errorMessage = "暂时无法生成")
            assertEquals(1, generateCalls)
        }
        composeRule.onNodeWithTag("phone_generate").assertIsEnabled()
    }

    @Test
    fun emptyCategory_refreshOnlyOffersItsOwnSection() {
        val snapshot = PhoneSnapshot(conversationId = "phone-test", notes = testSnapshot.notes)
        showPhone(state = { PhoneCheckUiState(isLoading = false, snapshot = snapshot) })
        selectSection(PhoneSnapshotSection.GALLERY)
        composeRule.onNodeWithText("还没有相册").assertIsDisplayed()
        composeRule.onNodeWithText("刷新相册").performClick()
        composeRule.onNodeWithTag("phone_refresh_gallery").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("phone_refresh_messages").performScrollTo().assertIsOff()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("还没有相册").assertIsDisplayed()
    }

    @Test
    fun allColorThemes_followAppAppearanceInsteadOfSystemAppearance() {
        var theme by mutableStateOf(AppColorTheme.MATCHA)
        var dark by mutableStateOf(false)
        var expectedBackground = Color.Unspecified
        showPhone(
            theme = { theme },
            dark = { dark },
            onBackground = { expectedBackground = it },
        )
        AppColorTheme.entries.forEach { colorTheme ->
            listOf(false, true).forEach { isDark ->
                composeRule.runOnIdle {
                    theme = colorTheme
                    dark = isDark
                }
                val pixels = composeRule.onNodeWithTag("phone_screen").captureToImage().toPixelMap()
                assertEquals(expectedBackground.toArgb(), pixels[2, pixels.height / 2].toArgb())
                captureReviewImage("phone-" + colorTheme.name.lowercase() + if (isDark) "-dark" else "-light")
            }
        }
    }

    private fun selectSection(section: PhoneSnapshotSection) {
        val tag = "phone_tab_" + section.storageValue
        composeRule.onNodeWithTag("phone_sections").performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
    }

    private fun showPhone(
        state: () -> PhoneCheckUiState = {
            PhoneCheckUiState(isLoading = false, ownerName = "陆星野与很长的角色显示名称", snapshot = testSnapshot)
        },
        fontScale: Float = 1f,
        dark: () -> Boolean = { false },
        theme: () -> AppColorTheme = { AppColorTheme.MATCHA },
        onBackground: (Color) -> Unit = {},
        onGenerate: () -> Unit = {},
        onRefresh: (Set<PhoneSnapshotSection>) -> Unit = {},
        onLoadSearch: (String) -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                ChatAppTheme(appColorTheme = theme(), darkTheme = dark()) {
                    onBackground(MaterialTheme.colorScheme.background)
                    Box(Modifier.widthIn(max = 360.dp).fillMaxSize()) {
                        PhoneCheckScreen(
                            uiState = state(),
                            onNavigateBack = onBack,
                            onGenerateSnapshot = onGenerate,
                            onRefreshSections = onRefresh,
                            onLoadSearchDetail = onLoadSearch,
                            onClearErrorMessage = {},
                            onClearNoticeMessage = {},
                            onOpenMailbox = {},
                        )
                    }
                }
            }
        }
    }

    private fun captureReviewImage(name: String, dialog: Boolean = false) {
        if (InstrumentationRegistry.getArguments().getString("uiScreenshots") != "true") return
        val node = if (dialog) composeRule.onNode(isDialog()) else composeRule.onNodeWithTag("phone_screen")
        val directory = File(composeRule.activity.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        val bitmap = node.captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val testSnapshot = PhoneSnapshot(
        conversationId = "phone-test",
        ownerName = "季望",
        messageThreads = listOf(
            PhoneMessageThread(
                id = "thread-1",
                contactName = "季望",
                relationLabel = "一起散步的朋友",
                preview = "傍晚见",
                timeLabel = "今天 17:30",
                messages = listOf(PhoneMessageItem("message-1", "季望", "等你一起散步", "17:30", false)),
            ),
        ),
        notes = listOf(PhoneNoteEntry("note-1", "周末出行的备忘录", "写给周末的自己", "记得带上相机和雨伞", "今天")),
        gallery = listOf(PhoneGalleryEntry("photo-1", "窗边的晴天", "午后的记录", "阳光停在杯沿", "昨天")),
        shoppingRecords = listOf(
            PhoneShoppingEntry("shopping-1", "一束白色小花", "已送达", "¥ 38", "送给平常的一天", "准备放在餐桌上", "昨天"),
        ),
        searchHistory = listOf(
            PhoneSearchEntry(
                "search-1",
                "附近安静的书店",
                "今天 10:00",
                PhoneSearchDetail("河边书店", "适合慢慢阅读", "沿着河边走十分钟"),
            ),
        ),
        socialPosts = listOf(
            PhoneSocialPost(
                id = "social-1",
                authorName = "一个有很长名字的朋友也能好好显示",
                authorLabel = "散步时偶遇的朋友",
                content = "今天的风很温柔",
                timeLabel = "今天 16:00",
                likeCount = 3,
                comments = listOf(PhoneSocialComment("social-comment-1", "季望", "下次也想一起去")),
            ),
        ),
    )
}
