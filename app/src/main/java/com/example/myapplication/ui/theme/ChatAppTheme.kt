package com.example.myapplication.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.example.myapplication.model.AppColorTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MatchaLightColors = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
)

private val MatchaDarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
)

@Composable
fun ChatAppTheme(
    appColorTheme: AppColorTheme = AppColorTheme.MATCHA,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            when (appColorTheme) {
                AppColorTheme.MATCHA -> if (darkTheme) MatchaDarkColors else MatchaLightColors
                AppColorTheme.VANILLA -> if (darkTheme) VanillaDarkColors else VanillaLightColors
                AppColorTheme.OCEAN -> if (darkTheme) OceanDarkColors else OceanLightColors
            }
        }
    }

    val activity = view.context as? Activity
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

private val VanillaLightColors = lightColorScheme(
    primary = Color(0xFFC7A87D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E8D8),
    onPrimaryContainer = Color(0xFF4A3B2C),
    secondary = Color(0xFFDCC8B3),
    onSecondary = Color(0xFF3D3124),
    secondaryContainer = Color(0xFFF6EFE6),
    onSecondaryContainer = Color(0xFF524536),
    tertiary = Color(0xFFB89B72),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8DBC9),
    onTertiaryContainer = Color(0xFF453625),
    error = Color(0xFFE57373),
    errorContainer = Color(0xFFFFEBEE),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFB71C1C),
    background = Color(0xFFFAF6F0),
    onBackground = Color(0xFF26221E),
    surface = Color(0xFFFAF6F0),
    onSurface = Color(0xFF26221E),
    surfaceVariant = Color(0xFFE8E1D7),
    onSurfaceVariant = Color(0xFF524B43),
    outline = Color(0xFF8A8277),
    inverseOnSurface = Color(0xFFF6F3EF),
    inverseSurface = Color(0xFF3B3733),
    inversePrimary = Color(0xFFE1C5A1),
)

private val VanillaDarkColors = darkColorScheme(
    primary = Color(0xFFE1C5A1),
    onPrimary = Color(0xFF3D3124),
    primaryContainer = Color(0xFF4A3B2C),
    onPrimaryContainer = Color(0xFFF3E8D8),
    secondary = Color(0xFFE6D5C3),
    onSecondary = Color(0xFF33271A),
    secondaryContainer = Color(0xFF453625),
    onSecondaryContainer = Color(0xFFF6EFE6),
    tertiary = Color(0xFFD1B792),
    onTertiary = Color(0xFF362817),
    tertiaryContainer = Color(0xFF4D3B26),
    onTertiaryContainer = Color(0xFFE8DBC9),
    error = Color(0xFFEF9A9A),
    errorContainer = Color(0xFFB71C1C),
    onError = Color(0xFF370B0B),
    onErrorContainer = Color(0xFFFFEBEE),
    background = Color(0xFF1E1B18),
    onBackground = Color(0xFFEBE6E0),
    surface = Color(0xFF1E1B18),
    onSurface = Color(0xFFEBE6E0),
    surfaceVariant = Color(0xFF524B43),
    onSurfaceVariant = Color(0xFFD4CCBF),
    outline = Color(0xFF9E958A),
    inverseOnSurface = Color(0xFF1E1B18),
    inverseSurface = Color(0xFFEBE6E0),
    inversePrimary = Color(0xFFC7A87D),
)

private val OceanLightColors = lightColorScheme(
    primary = Color(0xFF6B9ED1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E5F6),
    onPrimaryContainer = Color(0xFF163254),
    secondary = Color(0xFF8AB5E0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2EFFB),
    onSecondaryContainer = Color(0xFF1C3A5A),
    tertiary = Color(0xFF5A8DBE),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCBE0F4),
    onTertiaryContainer = Color(0xFF122C4A),
    error = Color(0xFFE57373),
    errorContainer = Color(0xFFFFEBEE),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFB71C1C),
    background = Color(0xFFF2F7FC),
    onBackground = Color(0xFF161B21),
    surface = Color(0xFFF2F7FC),
    onSurface = Color(0xFF161B21),
    surfaceVariant = Color(0xFFDDE6EF),
    onSurfaceVariant = Color(0xFF3D4958),
    outline = Color(0xFF758394),
    inverseOnSurface = Color(0xFFEFF3F8),
    inverseSurface = Color(0xFF28303A),
    inversePrimary = Color(0xFFA1C6ED),
)

private val OceanDarkColors = darkColorScheme(
    primary = Color(0xFFA1C6ED),
    onPrimary = Color(0xFF163254),
    primaryContainer = Color(0xFF2B4D73),
    onPrimaryContainer = Color(0xFFD5E5F6),
    secondary = Color(0xFFB5D3F2),
    onSecondary = Color(0xFF162740),
    secondaryContainer = Color(0xFF28486A),
    onSecondaryContainer = Color(0xFFE2EFFB),
    tertiary = Color(0xFF93BAE5),
    onTertiary = Color(0xFF122C4A),
    tertiaryContainer = Color(0xFF234466),
    onTertiaryContainer = Color(0xFFCBE0F4),
    error = Color(0xFFEF9A9A),
    errorContainer = Color(0xFFB71C1C),
    onError = Color(0xFF370B0B),
    onErrorContainer = Color(0xFFFFEBEE),
    background = Color(0xFF0F1318),
    onBackground = Color(0xFFDFE6EE),
    surface = Color(0xFF0F1318),
    onSurface = Color(0xFFDFE6EE),
    surfaceVariant = Color(0xFF3D4958),
    onSurfaceVariant = Color(0xFFC2CDDA),
    outline = Color(0xFF8B9BAE),
    inverseOnSurface = Color(0xFF0F1318),
    inverseSurface = Color(0xFFDFE6EE),
    inversePrimary = Color(0xFF6B9ED1),
)
