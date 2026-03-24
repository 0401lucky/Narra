package com.example.myapplication.ui.component.roleplay

import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.rememberUserProfileAvatarState
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
data class ImmersiveGlassPalette(
    val panelTint: Color,
    val panelTintStrong: Color,
    val panelHighlight: Color,
    val panelBorder: Color,
    val shadowColor: Color,
    val scrimTop: Color,
    val scrimBottom: Color,
    val onGlass: Color,
    val onGlassMuted: Color,
    val chipTint: Color,
    val chipText: Color,
)

@Immutable
data class ImmersiveBackdropState(
    val imageBitmap: ImageBitmap?,
    val screenSize: IntSize,
    val palette: ImmersiveGlassPalette,
) {
    val hasImage: Boolean
        get() = imageBitmap != null
}

@Composable
fun rememberImmersiveBackdropState(
    backgroundUri: String,
): ImmersiveBackdropState {
    val backgroundState = rememberUserProfileAvatarState(
        avatarUri = backgroundUri,
        avatarUrl = "",
    )
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val screenSize = remember(configuration, density) {
        with(density) {
            IntSize(
                width = configuration.screenWidthDp.dp.roundToPx(),
                height = configuration.screenHeightDp.dp.roundToPx(),
            )
        }
    }
    val palette = remember(backgroundState.imageBitmap, colorScheme) {
        deriveImmersiveGlassPalette(
            imageBitmap = backgroundState.imageBitmap.takeIf {
                backgroundState.loadState == UserAvatarLoadState.Success
            },
            colorScheme = colorScheme,
        )
    }
    return remember(backgroundState.imageBitmap, screenSize, palette) {
        ImmersiveBackdropState(
            imageBitmap = backgroundState.imageBitmap.takeIf {
                backgroundState.loadState == UserAvatarLoadState.Success
            },
            screenSize = screenSize,
            palette = palette,
        )
    }
}

@Composable
fun ImmersiveBackdrop(
    backdropState: ImmersiveBackdropState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (backdropState.imageBitmap != null) {
            Image(
                bitmap = backdropState.imageBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A2744),
                                Color(0xFF12203A),
                            )
                        )
                    )
            ) {
                // 左上角蓝色光晕
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF3B6B9F).copy(alpha = 0.35f), Color.Transparent),
                                radius = backdropState.screenSize.width * 1.5f,
                                center = Offset(0f, 0f)
                            )
                        )
                )
                // 右下角紫色光晕
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF6D4AAE).copy(alpha = 0.18f), Color.Transparent),
                                radius = backdropState.screenSize.width * 1.2f,
                                center = Offset(backdropState.screenSize.width.toFloat(), backdropState.screenSize.height.toFloat())
                            )
                        )
                )
                // 中心暖色点缀
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF5C7EA3).copy(alpha = 0.12f), Color.Transparent),
                                radius = backdropState.screenSize.width * 0.8f,
                                center = Offset(
                                    backdropState.screenSize.width * 0.5f,
                                    backdropState.screenSize.height * 0.3f,
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun ImmersiveGlassSurface(
    backdropState: ImmersiveBackdropState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    blurRadius: Dp = 28.dp,
    overlayColor: Color = Color.Black.copy(alpha = 0.15f),
    shadowElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Surface(
        modifier = modifier,
        color = Color.Transparent,
        shape = shape,
        shadowElevation = shadowElevation,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .onGloballyPositioned { coordinates ->
                    origin = coordinates.positionInRoot().let { offset ->
                        IntOffset(offset.x.roundToInt(), offset.y.roundToInt())
                    }
                    size = coordinates.size
                }
                .drawWithContent {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                backdropState.palette.panelHighlight.copy(alpha = 0.08f),
                                Color.Transparent,
                                backdropState.palette.shadowColor.copy(alpha = 0.04f),
                            ),
                        ),
                    )
                    drawContent()
                },
        ) {
            if (size.width > 0 && size.height > 0) {
                if (backdropState.imageBitmap != null) {
                    AlignedBlurredBackdropSlice(
                        backdropState = backdropState,
                        origin = origin,
                        size = size,
                        blurRadiusPx = blurRadiusPx,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(overlayColor)
            )

            content()
        }
    }
}

@Composable
fun ImmersiveGlassChip(
    text: String,
    backdropState: ImmersiveBackdropState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = backdropState.palette.chipTint.copy(alpha = 0.76f),
        contentColor = backdropState.palette.chipText,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                text = text,
                color = backdropState.palette.chipText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun AlignedBlurredBackdropSlice(
    backdropState: ImmersiveBackdropState,
    origin: IntOffset,
    size: IntSize,
    blurRadiusPx: Float,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = backdropState.imageBitmap ?: return
    val imageWidth = imageBitmap.width.toFloat()
    val imageHeight = imageBitmap.height.toFloat()
    if (imageWidth <= 0f || imageHeight <= 0f) {
        return
    }

    val screenWidth = backdropState.screenSize.width.toFloat().coerceAtLeast(1f)
    val screenHeight = backdropState.screenSize.height.toFloat().coerceAtLeast(1f)
    val scale = max(screenWidth / imageWidth, screenHeight / imageHeight)
    val scaledWidth = imageWidth * scale
    val scaledHeight = imageHeight * scale
    val imageOffsetX = ((screenWidth - scaledWidth) / 2f) - origin.x
    val imageOffsetY = ((screenHeight - scaledHeight) / 2f) - origin.y

    val globalImageOffsetX = (screenWidth - scaledWidth) / 2f
    val globalImageOffsetY = (screenHeight - scaledHeight) / 2f
    val srcOffsetX = ((origin.x - globalImageOffsetX) / scale)
        .coerceIn(0f, imageWidth - 1f)
    val srcOffsetY = ((origin.y - globalImageOffsetY) / scale)
        .coerceIn(0f, imageHeight - 1f)
    val srcWidth = (size.width / scale)
        .coerceIn(1f, imageWidth - srcOffsetX)
    val srcHeight = (size.height / scale)
        .coerceIn(1f, imageHeight - srcOffsetY)

    Canvas(
        modifier = modifier
            .graphicsLayer {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = android.graphics.RenderEffect
                        .createBlurEffect(
                            blurRadiusPx,
                            blurRadiusPx,
                            Shader.TileMode.CLAMP,
                        )
                        .asComposeRenderEffect()
                }
            },
    ) {
        drawImage(
            image = imageBitmap,
            srcOffset = androidx.compose.ui.unit.IntOffset(
                x = srcOffsetX.roundToInt(),
                y = srcOffsetY.roundToInt(),
            ),
            srcSize = IntSize(
                width = srcWidth.roundToInt(),
                height = srcHeight.roundToInt(),
            ),
            dstSize = IntSize(
                width = size.width,
                height = size.height,
            ),
        )
    }
}

private fun deriveImmersiveGlassPalette(
    imageBitmap: ImageBitmap?,
    colorScheme: ColorScheme,
): ImmersiveGlassPalette {
    val coldBase = Color(0xFF7A9BBD)
    val coldDeep = Color(0xFF4A6A8A)
    val sampled = imageBitmap?.let(::sampleAverageColor)
        ?: lerp(colorScheme.primary, coldBase, 0.42f)
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(sampled.toArgb(), hsl)
    hsl[0] = ((hsl[0] * 0.35f) + (216f * 0.65f))
    hsl[1] = (hsl[1] * 0.42f).coerceIn(0.10f, 0.28f)
    hsl[2] = (hsl[2] * 0.68f).coerceIn(0.22f, 0.48f)
    val dynamic = Color(ColorUtils.HSLToColor(hsl))
    val panelTint = lerp(coldBase, dynamic, 0.28f)
    val panelTintStrong = lerp(coldDeep, dynamic, 0.16f)
    val luminance = panelTintStrong.luminance()
    val onGlass = if (luminance > 0.34f) Color(0xFFF4F7FC) else Color(0xFFF8FBFF)
    val onGlassMuted = Color(0xDDE7EEF9)
    return ImmersiveGlassPalette(
        panelTint = panelTint.copy(alpha = 0.72f),
        panelTintStrong = panelTintStrong.copy(alpha = 0.84f),
        panelHighlight = Color.White.copy(alpha = 0.18f),
        panelBorder = Color.White.copy(alpha = 0.16f),
        shadowColor = Color.Black.copy(alpha = 0.34f),
        scrimTop = Color.Black.copy(alpha = 0.18f),
        scrimBottom = Color.Black.copy(alpha = 0.36f),
        onGlass = onGlass,
        onGlassMuted = onGlassMuted,
        chipTint = lerp(coldDeep, panelTintStrong, 0.42f).copy(alpha = 0.9f),
        chipText = Color.White.copy(alpha = 0.94f),
    )
}

private fun sampleAverageColor(
    imageBitmap: ImageBitmap,
): Color {
    val bitmap = imageBitmap.asAndroidBitmap()
    val stepX = max(1, bitmap.width / 24)
    val stepY = max(1, bitmap.height / 24)
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L

    var x = 0
    while (x < bitmap.width) {
        var y = 0
        while (y < bitmap.height) {
            val color = bitmap.getPixel(x, y)
            red += android.graphics.Color.red(color)
            green += android.graphics.Color.green(color)
            blue += android.graphics.Color.blue(color)
            count++
            y += stepY
        }
        x += stepX
    }

    if (count == 0L) {
        return Color(0xFF5B7694)
    }

    return Color(
        red = (red / count).toInt(),
        green = (green / count).toInt(),
        blue = (blue / count).toInt(),
    )
}
