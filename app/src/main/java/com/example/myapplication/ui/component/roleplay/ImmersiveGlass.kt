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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.get
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.rememberUserProfileAvatarState
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 调色板缓存：访问顺序驱逐，避免热点场景被随机移除
private val paletteCache = object : LinkedHashMap<String, ImmersiveGlassPalette>(
    ImmersivePaletteCacheMaxEntries,
    0.75f,
    true,
) {
    override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<String, ImmersiveGlassPalette>?,
    ): Boolean {
        return size > ImmersivePaletteCacheMaxEntries
    }
}

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
    val characterAccent: Color,
    val userAccent: Color,
    val thoughtText: Color,
    val readingSurface: Color,
    val readingBorder: Color,
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
    highContrast: Boolean = false,
): ImmersiveBackdropState {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val colorScheme = MaterialTheme.colorScheme
    val screenSize = remember(windowInfo.containerSize, density) {
        with(density) {
            IntSize(
                width = windowInfo.containerSize.width,
                height = windowInfo.containerSize.height,
            )
        }
    }
    val backgroundState = rememberUserProfileAvatarState(
        avatarUri = backgroundUri,
        avatarUrl = "",
        requestSize = screenSize,
        allowHardware = false,
    )

    // 异步计算调色板，带缓存
    val cacheKey = backgroundState.imageBitmap?.let {
        "${backgroundUri}_${it.width}_${it.height}_$highContrast"
    } ?: "fallback_${colorScheme.primary.toArgb()}_${colorScheme.secondary.toArgb()}_$highContrast"

    val palette by produceState<ImmersiveGlassPalette>(
        initialValue = readPaletteCache(cacheKey) ?: deriveImmersiveGlassPalette(
            imageBitmap = null,
            colorScheme = colorScheme,
            highContrast = highContrast,
        ),
        key1 = cacheKey,
        key2 = colorScheme,
    ) {
        // 先尝试从缓存读取
        readPaletteCache(cacheKey)?.let {
            value = it
            return@produceState
        }

        // 异步计算（在后台线程采样）
        val bitmap = backgroundState.imageBitmap.takeIf {
            backgroundState.loadState == UserAvatarLoadState.Success
        }
        val computed = withContext(Dispatchers.Default) {
            deriveImmersiveGlassPalette(
                imageBitmap = bitmap,
                colorScheme = colorScheme,
                highContrast = highContrast,
            )
        }

        // 写入缓存
        writePaletteCache(cacheKey, computed)
        value = computed
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

private fun readPaletteCache(key: String): ImmersiveGlassPalette? {
    return synchronized(paletteCache) {
        paletteCache[key]
    }
}

private fun writePaletteCache(
    key: String,
    palette: ImmersiveGlassPalette,
) {
    synchronized(paletteCache) {
        paletteCache[key] = palette
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
                                backdropState.palette.panelHighlight.copy(alpha = 0.14f),
                                Color.Transparent,
                                backdropState.palette.shadowColor.copy(alpha = 0.06f),
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
    highContrast: Boolean = false,
): ImmersiveGlassPalette {
    val fallbackBase = lerp(colorScheme.primary, colorScheme.secondary, 0.28f)
    val sampled = imageBitmap?.let(::sampleAverageColor) ?: fallbackBase

    val baseHsl = FloatArray(3)
    ColorUtils.colorToHSL(sampled.toArgb(), baseHsl)
    val hue = baseHsl[0]

    // 高对比度模式：提高饱和度和明度差异，增强可读性
    val contrastMultiplier = if (highContrast) 1.5f else 1.0f
    val panelSaturation = ((baseHsl[1] * 0.62f) * contrastMultiplier).coerceIn(
        if (highContrast) 0.25f else 0.12f,
        if (highContrast) 0.50f else 0.34f,
    )
    val panelLightness = ((baseHsl[2] * 0.56f) * contrastMultiplier).coerceIn(
        if (highContrast) 0.12f else 0.18f,
        if (highContrast) 0.40f else 0.34f,
    )

    val panelTint = colorFromHsl(
        hue = hue,
        saturation = panelSaturation,
        lightness = (panelLightness + 0.06f).coerceAtMost(if (highContrast) 0.30f else 0.40f),
    )
    val panelTintStrong = colorFromHsl(
        hue = hue,
        saturation = (panelSaturation + 0.03f).coerceAtMost(if (highContrast) 0.55f else 0.38f),
        lightness = panelLightness,
    )
    val characterAccent = colorFromHsl(
        hue = hue,
        saturation = ((baseHsl[1] * 0.9f) * contrastMultiplier).coerceIn(
            if (highContrast) 0.40f else 0.28f,
            if (highContrast) 0.75f else 0.62f,
        ),
        lightness = if (highContrast) 0.85f else 0.76f,
    )
    val userAccent = colorFromHsl(
        hue = (hue + 18f) % 360f,
        saturation = ((baseHsl[1] * 0.58f) * contrastMultiplier).coerceIn(
            if (highContrast) 0.30f else 0.18f,
            if (highContrast) 0.55f else 0.42f,
        ),
        lightness = if (highContrast) 0.88f else 0.80f,
    )
    val onGlass = if (highContrast) Color(0xFFFFFFFF) else Color(0xFFF8FBFF)
    val onGlassMuted = if (highContrast) {
        lerp(onGlass, characterAccent, 0.10f).copy(alpha = 0.92f)
    } else {
        lerp(onGlass, characterAccent, 0.18f).copy(alpha = 0.78f)
    }
    val chipTint = if (highContrast) {
        lerp(panelTintStrong, characterAccent, 0.20f).copy(alpha = 0.96f)
    } else {
        lerp(panelTintStrong, characterAccent, 0.30f).copy(alpha = 0.92f)
    }
    val thoughtText = if (highContrast) {
        lerp(onGlassMuted, userAccent, 0.15f).copy(alpha = 0.95f)
    } else {
        lerp(onGlassMuted, userAccent, 0.22f).copy(alpha = 0.88f)
    }

    // 高对比度：增强边框、阴影和 scrim
    val borderAlpha = if (highContrast) 0.35f else 0.15f
    val shadowAlpha = if (highContrast) 0.55f else 0.36f
    val scrimTopAlpha = if (highContrast) 0.30f else 0.16f
    val scrimBottomAlpha = if (highContrast) 0.60f else 0.40f
    val readingSurfaceAlpha = if (highContrast) 0.90f else 0.78f
    val readingBorderAlpha = if (highContrast) 0.45f else 0.22f

    return ImmersiveGlassPalette(
        panelTint = panelTint.copy(alpha = if (highContrast) 0.88f else 0.62f),
        panelTintStrong = panelTintStrong.copy(alpha = if (highContrast) 0.94f else 0.88f),
        panelHighlight = Color.White.copy(alpha = if (highContrast) 0.28f else 0.24f),
        panelBorder = Color.White.copy(alpha = borderAlpha),
        shadowColor = Color.Black.copy(alpha = shadowAlpha),
        scrimTop = Color.Black.copy(alpha = scrimTopAlpha),
        scrimBottom = Color.Black.copy(alpha = scrimBottomAlpha),
        onGlass = onGlass,
        onGlassMuted = onGlassMuted,
        chipTint = chipTint,
        chipText = Color.White.copy(alpha = if (highContrast) 1.0f else 0.96f),
        characterAccent = characterAccent,
        userAccent = userAccent,
        thoughtText = thoughtText,
        readingSurface = panelTintStrong.copy(alpha = readingSurfaceAlpha),
        readingBorder = characterAccent.copy(alpha = readingBorderAlpha),
    )
}

private fun colorFromHsl(
    hue: Float,
    saturation: Float,
    lightness: Float,
): Color {
    return Color(
        ColorUtils.HSLToColor(
            floatArrayOf(
                hue,
                saturation.coerceIn(0f, 1f),
                lightness.coerceIn(0f, 1f),
            ),
        ),
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
            val color = bitmap[x, y]
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

/**
 * 计算顶部区域平均亮度，用于系统栏图标明暗判断
 */
fun calculateTopRegionLuminance(imageBitmap: ImageBitmap?, regionHeightFraction: Float = 0.08f): Float {
    val bitmap = imageBitmap?.asAndroidBitmap() ?: return 0.0f
    val regionHeight = (bitmap.height * regionHeightFraction).toInt().coerceAtLeast(1)
    val stepX = max(1, bitmap.width / 32)
    val stepY = max(1, regionHeight / 8)
    var luminanceSum = 0.0
    var count = 0

    var x = 0
    while (x < bitmap.width) {
        var y = 0
        while (y < regionHeight) {
            val color = bitmap[x, y]
            val r = android.graphics.Color.red(color) / 255.0
            val g = android.graphics.Color.green(color) / 255.0
            val b = android.graphics.Color.blue(color) / 255.0
            // 相对亮度公式
            val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            luminanceSum += lum
            count++
            y += stepY
        }
        x += stepX
    }

    return if (count > 0) (luminanceSum / count).toFloat() else 0.0f
}

/**
 * 计算底部区域平均亮度
 */
fun calculateBottomRegionLuminance(imageBitmap: ImageBitmap?, regionHeightFraction: Float = 0.12f): Float {
    val bitmap = imageBitmap?.asAndroidBitmap() ?: return 0.0f
    val regionHeight = (bitmap.height * regionHeightFraction).toInt().coerceAtLeast(1)
    val startY = bitmap.height - regionHeight
    val stepX = max(1, bitmap.width / 32)
    val stepY = max(1, regionHeight / 8)
    var luminanceSum = 0.0
    var count = 0

    var x = 0
    while (x < bitmap.width) {
        var y = startY
        while (y < bitmap.height) {
            val color = bitmap[x, y]
            val r = android.graphics.Color.red(color) / 255.0
            val g = android.graphics.Color.green(color) / 255.0
            val b = android.graphics.Color.blue(color) / 255.0
            val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            luminanceSum += lum
            count++
            y += stepY
        }
        x += stepX
    }

    return if (count > 0) (luminanceSum / count).toFloat() else 0.0f
}
