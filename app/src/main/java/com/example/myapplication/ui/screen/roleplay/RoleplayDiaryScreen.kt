package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.rememberRoleplayDiaryAnnotatedString
import com.example.myapplication.ui.component.roleplay.stripRoleplayDiaryMarkers

@Composable
fun RoleplayDiaryScreen(
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
    diaryEntries: List<RoleplayDiaryEntry>,
    isGeneratingDiary: Boolean,
    noticeMessage: String?,
    errorMessage: String?,
    onClearNoticeMessage: () -> Unit,
    onClearErrorMessage: () -> Unit,
    onGenerateDiary: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    if (scenario == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("当前场景不存在")
        }
        return
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(noticeMessage) {
        noticeMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearNoticeMessage()
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearErrorMessage()
        }
    }
    val effectiveHighContrast = settings.roleplayHighContrast || rememberSystemHighTextContrastEnabled()
    val backdropState = rememberImmersiveBackdropState(
        backgroundUri = scenario.backgroundUri,
        highContrast = effectiveHighContrast,
    )
    val palette = backdropState.palette
    val characterName = scenario.characterDisplayNameOverride.trim()
        .ifBlank { assistant?.name?.trim().orEmpty() }
        .ifBlank { stringResource(R.string.roleplay_character_fallback) }

    // 根据壁纸亮度自适应遮罩，避免浅色壁纸下白字糊掉。
    val scrimAlpha = if (palette.onGlass.luminance() > 0.5f) 0.36f else 0.18f
    val hasDiary = diaryEntries.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        RoleplaySceneBackground(
            backdropState = backdropState,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ImmersiveGlassSurface(
                backdropState = backdropState,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                blurRadius = 22.dp,
                overlayColor = palette.panelTintStrong.copy(alpha = 0.76f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NarraIconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.common_back),
                            tint = palette.onGlass,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "角色日记",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.onGlass,
                            )
                            if (hasDiary) {
                                Text(
                                    text = "· 共 ${diaryEntries.size} 篇",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.onGlassMuted,
                                )
                            }
                        }
                        Text(
                            text = characterName,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onGlassMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 空态下顶部不再显示 CTA，由空态卡片唯一入口触发，避免双 CTA。
                    if (hasDiary || isGeneratingDiary) {
                        NarraTextButton(
                            onClick = onGenerateDiary,
                            enabled = !isGeneratingDiary,
                        ) {
                            if (isGeneratingDiary) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.onGlass,
                                )
                                Text(
                                    text = "生成中…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.onGlassMuted,
                                )
                            } else {
                                Text("重新生成")
                            }
                        }
                    }
                }
            }

            if (!hasDiary) {
                ImmersiveGlassSurface(
                    backdropState = backdropState,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    blurRadius = 24.dp,
                    overlayColor = palette.readingSurface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = palette.characterAccent,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "还没有生成过日记",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onGlass,
                        )
                        Text(
                            text = "会结合当前角色设定、长期上下文和最近剧情，生成一组更私密的角色日记。",
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                            color = palette.onGlassMuted,
                            textAlign = TextAlign.Center,
                        )
                        NarraTextButton(
                            onClick = onGenerateDiary,
                            enabled = !isGeneratingDiary,
                            modifier = Modifier.fillMaxWidth(0.65f),
                        ) {
                            if (isGeneratingDiary) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.onGlass,
                                )
                                Text("生成中…")
                            } else {
                                Text("开始生成")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        items = diaryEntries,
                        key = { it.id },
                    ) { entry ->
                        RoleplayDiaryEntryCard(
                            entry = entry,
                            backdropState = backdropState,
                        )
                    }
                }
            }
        }

        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun RoleplayDiaryEntryCard(
    entry: RoleplayDiaryEntry,
    backdropState: com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState,
) {
    val palette = backdropState.palette
    var revealMasked by rememberSaveable(entry.id) { mutableStateOf(false) }
    val annotated = rememberRoleplayDiaryAnnotatedString(
        text = entry.content,
        revealMasked = revealMasked,
        primaryText = palette.onGlass,
        accent = palette.characterAccent,
    )
    val hasMaskedContent = remember(entry.content) { entry.content.contains("||") }
    val screenReaderContent = remember(entry.content, revealMasked) {
        stripRoleplayDiaryMarkers(entry.content, revealMasked = revealMasked)
    }
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        blurRadius = 24.dp,
        overlayColor = palette.readingSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.onGlass,
            )
            // 使用 Color.Unspecified，让 SpanStyle 里的 color 生效；未标记部分仍使用 palette.onGlass。
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 28.sp,
                    letterSpacing = 0.2.sp,
                ),
                color = palette.onGlass,
                modifier = Modifier.semantics {
                    contentDescription = screenReaderContent
                },
            )
            if (hasMaskedContent) {
                NarraTextButton(onClick = { revealMasked = !revealMasked }) {
                    Text(if (revealMasked) "隐藏涂黑" else "显示涂黑")
                }
            }
        }
    }
}
