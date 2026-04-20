package com.example.myapplication.ui.component.roleplay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 沉浸式玻璃态选项 Tab 行。
 *
 * 设置页里交互模式、沉浸模式、行高三处 Tab 原本是重复粘贴的 40+ 行结构，
 * 统一收口到此处以保持视觉一致、动效一致。
 *
 * @param entries 选项枚举值列表
 * @param selected 当前选中的值
 * @param label 选项显示文本
 * @param keyOf 动画/testTag 的稳定 key（通常是枚举的 storageValue 或 name）
 * @param testTagPrefix 若非空则为每个 Tab 追加 `"${testTagPrefix}_${keyOf(entry)}"` 的 testTag
 * @param enabled 是否可点击（场景未加载时常需置灰）
 * @param onSelect 选中回调
 */
@Composable
internal fun <T> ImmersiveTabRow(
    entries: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    keyOf: (T) -> String,
    palette: ImmersiveGlassPalette,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
    enabled: Boolean = true,
    itemHorizontalPadding: Dp = 12.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { entry ->
            val isSelected = entry == selected
            val key = keyOf(entry)
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) {
                    palette.characterAccent.copy(alpha = RoleplayGlassTokens.TabSelectedAccentAlpha)
                } else {
                    Color.Transparent
                },
                label = "immersive_tab_border_$key",
            )
            val bgAlpha by animateFloatAsState(
                targetValue = if (isSelected) {
                    RoleplayGlassTokens.TabSelectedBgAlpha
                } else {
                    RoleplayGlassTokens.TabUnselectedBgAlpha
                },
                label = "immersive_tab_bg_$key",
            )
            // 选中态给一个轻微的 scale 反馈（1.0 → 1.02），让点击更有份量。
            // 使用 spring 而非 tween，中等刚度 + 较低阻尼产生微弹感。
            val selectionScale by animateFloatAsState(
                targetValue = if (isSelected) 1.02f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "immersive_tab_scale_$key",
            )
            val tabModifier = Modifier
                .weight(1f)
                .scale(selectionScale)
                .semantics { this.selected = isSelected }
                .let { base ->
                    if (testTagPrefix != null) {
                        base.testTag("${testTagPrefix}_$key")
                    } else {
                        base
                    }
                }
            Surface(
                modifier = tabModifier,
                onClick = { onSelect(entry) },
                shape = RoundedCornerShape(RoleplayGlassTokens.TabCornerRadius),
                color = palette.panelTintStrong.copy(alpha = bgAlpha),
                enabled = enabled,
                border = BorderStroke(
                    width = if (isSelected) RoleplayGlassTokens.TabSelectedBorderWidth else 0.dp,
                    color = borderColor,
                ),
            ) {
                Text(
                    text = label(entry),
                    modifier = Modifier.padding(
                        horizontal = itemHorizontalPadding,
                        vertical = 12.dp,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) palette.characterAccent else palette.onGlassMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
