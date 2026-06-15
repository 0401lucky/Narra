package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Android 12 以下的回退色板：采用治愈系抹茶绿 (Healing Matcha Green)
val md_theme_light_primary = Color(0xFF8CC28A)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFDDEEDD)
val md_theme_light_onPrimaryContainer = Color(0xFF2D4A2B)
val md_theme_light_secondary = Color(0xFFA5D6A7)
val md_theme_light_onSecondary = Color(0xFF1E331F)
val md_theme_light_secondaryContainer = Color(0xFFE8F5E9)
val md_theme_light_onSecondaryContainer = Color(0xFF2E4D30)
val md_theme_light_tertiary = Color(0xFF7CB342)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFDCEDC8)
val md_theme_light_onTertiaryContainer = Color(0xFF33691E)
val md_theme_light_error = Color(0xFFE57373)
val md_theme_light_errorContainer = Color(0xFFFFEBEE)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFFB71C1C)
val md_theme_light_background = Color(0xFFF4F9F4)
val md_theme_light_onBackground = Color(0xFF1A1C19)
val md_theme_light_surface = Color(0xFFF4F9F4)
val md_theme_light_onSurface = Color(0xFF1A1C19)
val md_theme_light_surfaceVariant = Color(0xFFDFE4DD)
val md_theme_light_onSurfaceVariant = Color(0xFF424940)
val md_theme_light_outline = Color(0xFF72796F)
val md_theme_light_inverseOnSurface = Color(0xFFF1F1EB)
val md_theme_light_inverseSurface = Color(0xFF2F312D)
val md_theme_light_inversePrimary = Color(0xFFA5D6A7)

val md_theme_dark_primary = Color(0xFFA5D6A7)
val md_theme_dark_onPrimary = Color(0xFF1E331F)
val md_theme_dark_primaryContainer = Color(0xFF2D4A2B)
val md_theme_dark_onPrimaryContainer = Color(0xFFDDEEDD)
val md_theme_dark_secondary = Color(0xFF81C784)
val md_theme_dark_onSecondary = Color(0xFF1A331A)
val md_theme_dark_secondaryContainer = Color(0xFF274C27)
val md_theme_dark_onSecondaryContainer = Color(0xFFB6ECC0)
val md_theme_dark_tertiary = Color(0xFFC5E1A5)
val md_theme_dark_onTertiary = Color(0xFF2A4B00)
val md_theme_dark_tertiaryContainer = Color(0xFF3D6B00)
val md_theme_dark_onTertiaryContainer = Color(0xFFE1FCD0)
val md_theme_dark_error = Color(0xFFEF9A9A)
val md_theme_dark_errorContainer = Color(0xFFB71C1C)
val md_theme_dark_onError = Color(0xFF370B0B)
val md_theme_dark_onErrorContainer = Color(0xFFFFEBEE)
val md_theme_dark_background = Color(0xFF121411)
val md_theme_dark_onBackground = Color(0xFFE2E3DD)
val md_theme_dark_surface = Color(0xFF121411)
val md_theme_dark_onSurface = Color(0xFFE2E3DD)
val md_theme_dark_surfaceVariant = Color(0xFF424940)
val md_theme_dark_onSurfaceVariant = Color(0xFFC2C8BE)
val md_theme_dark_outline = Color(0xFF8C9388)
val md_theme_dark_inverseOnSurface = Color(0xFF121411)
val md_theme_dark_inverseSurface = Color(0xFFE2E3DD)
val md_theme_dark_inversePrimary = Color(0xFF8CC28A)

// ── Moments / PhoneCheck 模块共用色：跟随当前全局主题色板 ──

@Composable fun MomentsBackground() = MaterialTheme.colorScheme.background
@Composable fun MomentsCardBackground() = MaterialTheme.colorScheme.surfaceContainerLow
@Composable fun MomentsAccent() = MaterialTheme.colorScheme.primary
@Composable fun MomentsAccentSoft() = MaterialTheme.colorScheme.primaryContainer
@Composable fun MomentsMutedText() = MaterialTheme.colorScheme.onSurfaceVariant
@Composable fun MomentsLikeRed() = MaterialTheme.colorScheme.error
