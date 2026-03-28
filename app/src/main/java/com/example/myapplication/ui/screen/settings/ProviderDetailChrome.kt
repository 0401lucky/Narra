package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.brandColor
import com.example.myapplication.ui.component.NarraIconButton

@Composable
internal fun ProviderDetailTopBar(
    provider: ProviderSettings,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val brandColor = provider.resolvedType().brandColor(isSystemInDarkTheme())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NarraIconButton(onClick = onNavigateBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = palette.title,
                    modifier = Modifier.size(24.dp),
                )
            }
            Surface(
                shape = CircleShape,
                color = brandColor.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp),
            ) {
                val iconRes = provider.resolvedType().iconRes
                if (iconRes != null) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            provider.name.firstOrNull()?.uppercase() ?: "?",
                            color = brandColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
            Text(
                text = provider.name.ifBlank { "New Provider" },
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 0.5.sp),
                fontWeight = FontWeight.Bold,
                color = palette.title,
            )
        }
        Icon(
            Icons.Outlined.IosShare,
            contentDescription = "Share",
            tint = palette.title,
            modifier = Modifier.size(22.dp),
        )
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
            .padding(horizontal = 48.dp, vertical = 24.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(40.dp),
        color = palette.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
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
    val contentColor = if (selected) palette.accentStrong else palette.body.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .clickable(onClick = onClick)
            .background(bg)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            Text(
                label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
