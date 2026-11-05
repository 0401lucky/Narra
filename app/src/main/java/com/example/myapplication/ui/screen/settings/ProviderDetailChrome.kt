package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.brandColor
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi

@Composable
internal fun ProviderDetailTopBar(
    provider: ProviderSettings,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val brandColor =
        provider.resolvedType().brandColor(MaterialTheme.colorScheme.surface.luminance() < 0.5f)
    val titleScroll = remember(provider.id) { ScrollState(0) }
    // 超长名称自身滚动，为表单和底栏保留可用高度，返回始终独立可达。
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val maxTitleHeight = (windowHeight * 0.4f)
        .coerceAtLeast(NarraMinimumTouchTarget)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = NarraUi.space16, vertical = NarraUi.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NarraUi.space12),
        ) {
            NarraIconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(NarraMinimumTouchTarget)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = palette.title,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (LocalDensity.current.fontScale <= 1.15f) Surface(
                shape = NarraShapes.pill,
                color = brandColor.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp),
            ) {
                val iconRes = provider.resolvedType().iconRes
                if (iconRes != null) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(NarraUi.space8)
                            .fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            provider.name.firstOrNull()?.uppercase() ?: "?",
                            color = palette.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.weight(1f).heightIn(max = maxTitleHeight)
                    .verticalScroll(titleScroll),
            ) {
                Text(
                    text = provider.name.ifBlank { "新提供商" },
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.title,
                )
            }
        }
    }
}

@Composable
internal fun SleekBottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = NarraUi.space16, vertical = NarraUi.space12)
            .fillMaxWidth(),
        shape = NarraShapes.medium,
        color = palette.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(NarraUi.space4).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SleekBottomNavItem(
                icon = Icons.Outlined.Build,
                label = "配置",
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(1f),
            )
            SleekBottomNavItem(
                icon = Icons.Outlined.Inventory2,
                label = "模型",
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SleekBottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberSettingsPalette()
    val bg = if (selected) palette.accentSoft else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else palette.body

    Box(
        modifier = modifier
            .heightIn(min = NarraMinimumTouchTarget)
            .clip(NarraShapes.small)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .background(bg)
            .padding(vertical = NarraUi.space12),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
