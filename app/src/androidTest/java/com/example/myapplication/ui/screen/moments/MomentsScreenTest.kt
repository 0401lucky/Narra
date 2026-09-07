package com.example.myapplication.ui.screen.moments

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
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.repository.LocalImageStore
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.MomentComment
import com.example.myapplication.model.MomentMedia
import com.example.myapplication.model.MomentMediaStatus
import com.example.myapplication.model.MomentPost
import com.example.myapplication.model.UserPersonaMask
import com.example.myapplication.ui.LocalImagePersister
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.MomentsUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun singlePost_deleteRequiresConfirmationAndCancelKeepsItVisible() {
        val deletedIds = mutableListOf<String>()
        showMoments(onDelete = { deletedIds += it })
        scrollTimelineTo("moment_post_post-1")
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("删除这条朋友圈？").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertEquals(0, deletedIds.size) }
        composeRule.onNodeWithTag("moment_post_post-1").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.runOnIdle { assertEquals(listOf("post-1"), deletedIds) }
    }

    @Test
    fun batchAndClear_largeFont_onlyDeleteAfterExplicitConfirmation() {
        val deletedBatches = mutableListOf<List<String>>()
        var clearCalls = 0
        val posts = listOf(
            testPost.copy(comments = emptyList()),
            testPost.copy(id = "post-2", content = "下一段旅程，慢慢出发。", comments = emptyList()),
        )
        showMoments(
            state = { MomentsUiState(isLoading = false, posts = posts) },
            fontScale = 1.6f,
            onDeleteMany = { deletedBatches += it },
            onClear = { clearCalls++ },
        )
        composeRule.onNodeWithContentDescription("朋友圈更多操作").performClick()
        composeRule.onNodeWithText("选择删除").performClick()
        composeRule.onNodeWithText("全选").performClick()
        composeRule.onNodeWithContentDescription("删除选中朋友圈").performClick()
        captureReviewImage("moments-batch-delete-large-font", dialog = true)
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertEquals(0, deletedBatches.size) }
        composeRule.onNodeWithText("已选 2 条").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("删除选中朋友圈").performClick()
        composeRule.onNodeWithText("删除选中").performClick()
        composeRule.runOnIdle { assertEquals(setOf("post-1", "post-2"), deletedBatches.single().toSet()) }

        composeRule.onNodeWithContentDescription("朋友圈更多操作").performClick()
        composeRule.onNodeWithText("清空朋友圈").performClick()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertEquals(0, clearCalls) }
        composeRule.onNodeWithContentDescription("朋友圈更多操作").performClick()
        composeRule.onNodeWithText("清空朋友圈").performClick()
        composeRule.onNodeWithText("清空").performClick()
        composeRule.runOnIdle { assertEquals(1, clearCalls) }
    }

    @Test
    fun composer_failurePreservesDraftLocationAndIdentity_andPublishingDisablesRepeat() {
        var state by mutableStateOf(
            MomentsUiState(
                isLoading = false,
                userPersonaMasks = listOf(
                    UserPersonaMask(id = "mask-default", name = "平常的我"),
                    UserPersonaMask(id = "mask-travel", name = "旅行中的我"),
                ),
                selectedUserPersonaMaskId = "mask-default",
            ),
        )
        val submissions = mutableListOf<List<String>>()
        showMoments(
            state = { state },
            fontScale = 1.6f,
            dark = { true },
            onPublish = { content, image, location, mask ->
                submissions += listOf(content, image, location, mask)
                state = state.copy(isPublishing = true)
            },
        )
        composeRule.onNodeWithContentDescription("发表朋友圈").performClick()
        composeRule.onNodeWithTag("moment_publish").assertIsNotEnabled()
        composeRule.onNodeWithTag("moment_draft").performTextInput("周末出发")
        scrollComposerTo("moment_location")
        composeRule.onNodeWithTag("moment_location").performTextInput("江边")
        scrollComposerTo("moment_identity")
        composeRule.onNodeWithTag("moment_identity").performClick()
        composeRule.onNodeWithText("旅行中的我").performClick()
        scrollComposerTo("moment_draft")
        captureReviewImage("moments-composer-dark-large-font")
        composeRule.onNodeWithTag("moment_publish").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithTag("moment_publish").assertIsNotEnabled()
        composeRule.onNodeWithTag("moment_draft").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(listOf(listOf("周末出发", "", "江边", "mask-travel")), submissions)
            state = state.copy(isPublishing = false, errorMessage = "暂时无法发表")
        }
        scrollComposerTo("moment_draft")
        composeRule.onNodeWithTag("moment_draft").assertTextContains("周末出发")
        composeRule.onNodeWithTag("moment_publish").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(submissions[0], submissions[1])
            state = state.copy(isPublishing = false, errorMessage = null, lastPublishedPostId = "published-post")
        }
        composeRule.onNodeWithTag("moment_composer").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("发表朋友圈").assertIsDisplayed()
    }

    @Test
    fun composer_immediatePublishSuccess_returnsToTimelineEvenWhenReplyFails() {
        var state by mutableStateOf(MomentsUiState(isLoading = false))
        var submissions = 0
        showMoments(
            state = { state },
            onPublish = { content, _, _, _ ->
                submissions++
                state = state.copy(isPublishing = true)
                state = state.copy(
                    isPublishing = false,
                    lastPublishedPostId = "immediate-post",
                    posts = listOf(testPost.copy(id = "immediate-post", content = content)),
                    errorMessage = "动态已保存，回复暂时不可用",
                )
            },
        )

        composeRule.onNodeWithContentDescription("发表朋友圈").performClick()
        composeRule.onNodeWithTag("moment_draft").performTextInput("刚刚完成的记录")
        composeRule.onNodeWithTag("moment_publish").performClick()
        composeRule.onNodeWithTag("moment_composer").assertDoesNotExist()
        scrollTimelineTo("moment_post_immediate-post")
        composeRule.onNodeWithText("刚刚完成的记录").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, submissions) }
    }

    @Test
    fun detail_largeFont_likeAndReplyKeepTheirCallbacksAndSystemBackReturnsToTimeline() {
        var state by mutableStateOf(MomentsUiState(isLoading = false, posts = listOf(testPost)))
        var likeCalls = 0
        var backCalls = 0
        val replies = mutableListOf<List<String>>()
        showMoments(
            state = { state },
            fontScale = 1.6f,
            onLike = {
                likeCalls++
                state = state.copy(posts = state.posts.map { post -> post.toggleLike("我") })
            },
            onComment = { postId, text, replyId ->
                replies += listOf(postId, text, replyId)
                state = state.copy(isGeneratingReplies = true, replyingPostId = postId)
            },
            onBack = { backCalls++ },
        )
        scrollTimelineTo("moment_like_post-1")
        composeRule.onNodeWithTag("moment_like_post-1").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.runOnIdle { assertEquals(1, likeCalls) }
        composeRule.onNodeWithTag("moment_post_post-1").performClick()
        composeRule.onNodeWithTag("moment_detail_list").performScrollToNode(hasTestTag("moment_comment_comment-1"))
        composeRule.onNodeWithTag("moment_comment_comment-1").performClick()
        composeRule.onNodeWithTag("moment_reply_target").assertIsDisplayed()
        composeRule.onNodeWithTag("moment_comment_input").performTextInput("我也想一起去")
        composeRule.onNodeWithTag("moment_send_comment").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        captureReviewImage("moments-reply-large-font")
        composeRule.onNodeWithTag("moment_send_comment").performClick()
        composeRule.onNodeWithTag("moment_comment_input").performTextInput("等你")
        composeRule.onNodeWithTag("moment_send_comment").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(listOf(listOf("post-1", "我也想一起去", "comment-1")), replies)
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithTag("moments_timeline").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, backCalls) }
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test
    fun mediaRetryAndCommentLongPress_doNotAccidentallyOpenPostOrDeleteOnCancel() {
        val post = testPost.copy(media = MomentMedia(status = MomentMediaStatus.FAILED))
        var state by mutableStateOf(MomentsUiState(isLoading = false, posts = listOf(post)))
        val retried = mutableListOf<String>()
        val deletedComments = mutableListOf<String>()
        showMoments(
            state = { state },
            onRetry = {
                retried += it
                state = state.copy(retryingImagePostId = it)
            },
            onDeleteComment = { deletedComments += it },
        )
        scrollTimelineTo("moment_retry_post-1")
        composeRule.onNodeWithTag("moment_retry_post-1").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithTag("moment_retry_post-1").assertIsNotEnabled()
        composeRule.onNodeWithTag("moment_comment_input").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(listOf("post-1"), retried) }
        scrollTimelineTo("moment_comment_preview_comment-1")
        composeRule.onNodeWithTag("moment_comment_preview_comment-1")
            .assertHeightIsAtLeast(48.dp).performTouchInput { longClick() }
        composeRule.onNodeWithText("删除这条回复？").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertEquals(0, deletedComments.size) }
        composeRule.onNodeWithTag("moment_comment_preview_comment-1").performTouchInput { longClick() }
        composeRule.onNodeWithText("删除").performClick()
        composeRule.runOnIdle { assertEquals(listOf("comment-1"), deletedComments) }
    }

    @Test
    fun allColorThemes_useThemeSurfaceAndCoverInsteadOfFixedGreen() {
        var theme by mutableStateOf(AppColorTheme.MATCHA)
        var dark by mutableStateOf(false)
        var expectedBackground = Color.Unspecified
        var expectedCover = Color.Unspecified
        showMoments(
            theme = { theme },
            dark = { dark },
            onColors = { background, cover ->
                expectedBackground = background
                expectedCover = cover
            },
        )
        AppColorTheme.entries.forEach { colorTheme ->
            listOf(false, true).forEach { isDark ->
                composeRule.runOnIdle {
                    theme = colorTheme
                    dark = isDark
                }
                val background = composeRule.onNodeWithTag("moments_screen").captureToImage().toPixelMap()
                val cover = composeRule.onNodeWithTag("moments_cover").captureToImage().toPixelMap()
                assertEquals(expectedBackground.toArgb(), background[2, background.height / 2].toArgb())
                assertEquals(expectedCover.toArgb(), cover[16, 16].toArgb())
                captureReviewImage("moments-" + colorTheme.name.lowercase() + if (isDark) "-dark" else "-light")
            }
        }
    }

    private fun scrollTimelineTo(tag: String) {
        composeRule.onNodeWithTag("moments_timeline").performScrollToNode(hasTestTag(tag))
    }

    private fun scrollComposerTo(tag: String) {
        composeRule.onNodeWithTag("moment_composer").performScrollToNode(hasTestTag(tag))
    }

    private fun showMoments(
        state: () -> MomentsUiState = { MomentsUiState(isLoading = false, posts = listOf(testPost)) },
        fontScale: Float = 1f,
        dark: () -> Boolean = { false },
        theme: () -> AppColorTheme = { AppColorTheme.MATCHA },
        onColors: (Color, Color) -> Unit = { _, _ -> },
        onPublish: (String, String, String, String) -> Unit = { _, _, _, _ -> },
        onLike: (String) -> Unit = {},
        onDelete: (String) -> Unit = {},
        onDeleteMany: (List<String>) -> Unit = {},
        onClear: () -> Unit = {},
        onComment: (String, String, String) -> Unit = { _, _, _ -> },
        onDeleteComment: (String) -> Unit = {},
        onRetry: (String) -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        val images = LocalImageStore(composeRule.activity)
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale),
                LocalImagePersister provides images,
            ) {
                ChatAppTheme(appColorTheme = theme(), darkTheme = dark()) {
                    onColors(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.secondaryContainer)
                    Box(Modifier.widthIn(max = 360.dp).fillMaxSize()) {
                        MomentsScreen(
                            uiState = state(),
                            viewerName = "我",
                            onNavigateBack = onBack,
                            onPublishPost = onPublish,
                            onToggleLikePost = onLike,
                            onDeletePost = onDelete,
                            onDeletePosts = onDeleteMany,
                            onClearMoments = onClear,
                            onAddComment = onComment,
                            onDeleteComment = onDeleteComment,
                            onRetryImage = onRetry,
                            onRefreshMoments = {},
                            onUpdateCoverImage = {},
                            onClearErrorMessage = {},
                            onOpenPhoneCheck = {},
                        )
                    }
                }
            }
        }
    }

    private fun captureReviewImage(name: String, dialog: Boolean = false) {
        if (InstrumentationRegistry.getArguments().getString("uiScreenshots") != "true") return
        val node = if (dialog) composeRule.onNode(isDialog()) else composeRule.onNodeWithTag("moments_screen")
        val directory = File(composeRule.activity.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        val bitmap = node.captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val testPost = MomentPost(
        id = "post-1",
        authorName = "陆星野与一个比较长的显示名称",
        content = "沿着河边走了一会儿，想把今天的风分享给你。",
        location = "河边的小书店",
        likedByNames = listOf("季望"),
        comments = listOf(
            MomentComment(
                id = "comment-1",
                postId = "post-1",
                authorName = "也有一段很长显示名称的季望",
                text = "等下一次晴天，我们一起去吧。",
            ),
        ),
    )
}
