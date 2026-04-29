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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import com.example.myapplication.ui.component.roleplay.ImmersiveReadingGlassSurface
import com.example.myapplication.ui.component.roleplay.ImmersiveReadingGlassVariant
import com.example.myapplication.ui.component.roleplay.ImmersiveReadingScrimVariant
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.calculateImmersiveBackdropAmbientLuminance
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.rememberRoleplayDiaryAnnotatedString
import com.example.myapplication.ui.component.roleplay.resolveImmersiveReadingScrimAlpha
import com.example.myapplication.ui.component.roleplay.stripRoleplayDiaryMarkers

/**
 * 单篇日记全屏阅读页。提供更大的字号/行距、上一篇/下一篇切换，以及涂黑显隐。
 */
@Composable
fun RoleplayDiaryDetailScreen(
    scenario: RoleplayScenario?,
    settings: AppSettings,
    diaryEntries: List<RoleplayDiaryEntry>,
    entryId: String,
    onOpenMailbox: () -> Unit,
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
    if (diaryEntries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("日记列表为空")
        }
        return
    }

    val initialIndex = remember(entryId, diaryEntries) {
        diaryEntries.indexOfFirst { it.id == entryId }.coerceAtLeast(0)
    }
    var currentIndex by rememberSaveable(entryId) { mutableIntStateOf(initialIndex) }
    val entry = diaryEntries.getOrNull(currentIndex) ?: diaryEntries.first()

    val effectiveHighContrast = settings.roleplayHighContrast || rememberSystemHighTextContrastEnabled()
    val backdropState = rememberImmersiveBackdropState(
        backgroundUri = scenario.backgroundUri,
        highContrast = effectiveHighContrast,
    )
    val palette = backdropState.palette
    val ambientLuminance = remember(backdropState.imageBitmap, palette) {
        calculateImmersiveBackdropAmbientLuminance(backdropState)
    }
    val scrimAlpha = remember(ambientLuminance) {
        resolveImmersiveReadingScrimAlpha(
            backgroundLuminance = ambientLuminance,
            variant = ImmersiveReadingScrimVariant.DIARY_DETAIL,
        )
    }
    val scrimBrush = remember(scrimAlpha) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Black.copy(alpha = scrimAlpha * 0.20f),
                0.38f to Color.Black.copy(alpha = scrimAlpha * 0.54f),
                1.0f to Color.Black.copy(alpha = scrimAlpha),
            ),
        )
    }

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
    val effectiveDateLabel = entry.dateLabel.ifBlank { formatFallbackDate(entry.createdAt) }

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
                .background(scrimBrush),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
                ImmersiveReadingGlassSurface(
                    backdropState = backdropState,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    variant = ImmersiveReadingGlassVariant.CHROME,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NarraIconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = palette.onGlass,
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                        ) {
                            if (effectiveDateLabel.isNotBlank()) {
                                Text(
                                    text = effectiveDateLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                    ),
                                    color = palette.onGlassMuted,
                                )
                            }
                            Text(
                                text = "${currentIndex + 1} / ${diaryEntries.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                ),
                                color = palette.onGlassMuted.copy(alpha = 0.84f),
                            )
                        }
                        if (entry.mood.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = palette.characterAccent.copy(alpha = 0.18f),
                            ) {
                                Text(
                                    text = "· ${entry.mood}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                    ),
                                    color = palette.onGlass,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        NarraIconButton(onClick = onOpenMailbox) {
                            Icon(
                                imageVector = Icons.Default.Mail,
                                contentDescription = "信箱",
                                tint = palette.onGlass,
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        ImmersiveReadingGlassSurface(
                            backdropState = backdropState,
                            modifier = Modifier.fillMaxWidth(0.965f),
                            shape = RoundedCornerShape(30.dp),
                            variant = ImmersiveReadingGlassVariant.PANEL,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onGlass,
                                )
                                if (entry.weather.isNotBlank() || entry.tags.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (entry.weather.isNotBlank()) {
                                            Text(
                                                text = entry.weather,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                ),
                                                color = palette.onGlassMuted,
                                            )
                                        }
                                        entry.tags.take(5).forEach { tag ->
                                            Text(
                                                text = "#$tag",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                ),
                                                color = palette.characterAccent,
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 17.sp,
                                        lineHeight = 32.sp,
                                        letterSpacing = 0.3.sp,
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
                }

            ImmersiveReadingGlassSurface(
                backdropState = backdropState,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                variant = ImmersiveReadingGlassVariant.CHROME,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NarraTextButton(
                        onClick = { if (currentIndex > 0) currentIndex -= 1 },
                        enabled = currentIndex > 0,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("上一篇")
                    }
                    Box(modifier = Modifier.weight(1f))
                    NarraTextButton(
                        onClick = {
                            if (currentIndex < diaryEntries.lastIndex) currentIndex += 1
                        },
                        enabled = currentIndex < diaryEntries.lastIndex,
                    ) {
                        Text("下一篇")
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
