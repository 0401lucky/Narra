package com.example.myapplication.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.ChatAppTheme

@Composable
fun GemClover(
    modifier: Modifier = Modifier
) {
    val goldColor = Color(0xFFC5A059)
    val goldGradient = Brush.radialGradient(
        colors = listOf(Color(0xFFF9E79F), Color(0xFFD4AF37), Color(0xFF9A7D0A)),
        center = Offset.Unspecified,
        radius = Float.POSITIVE_INFINITY
    )
    
    val greens = listOf(
        Color(0xFF064D2C), // Deep emerald
        Color(0xFF0B6636), 
        Color(0xFF138D4F), // Forest green
        Color(0xFF19AA5E),
        Color(0xFF27C073), // Vivid emerald
        Color(0xFF52D68F)  // Light minty emerald
    )

    Canvas(modifier = modifier.size(400.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * 0.42f

        for (i in 0 until 4) {
            withTransform({
                rotate(degrees = i * 90f, pivot = center)
            }) {
                drawCloverLeaf(
                    center = center,
                    radius = radius,
                    greens = greens,
                    goldColor = goldColor
                )
            }
        }

        drawCenterStar(center, radius * 0.18f, goldGradient)
    }
}

private fun DrawScope.drawCloverLeaf(
    center: Offset,
    radius: Float,
    greens: List<Color>,
    goldColor: Color
) {
    val c1 = greens[0]
    val c2 = greens[1]
    val c3 = greens[2]
    val c4 = greens[3]
    val c5 = greens[4]
    val c6 = greens[5]

    // Define points for one heart-shaped leaf
    val pCenter = center
    val pMidVein1 = center + Offset(0f, -radius * 0.3f)
    val pMidVein2 = center + Offset(0f, -radius * 0.6f)
    val pIndentation = center + Offset(0f, -radius * 0.82f)
    
    val pTopL = center + Offset(-radius * 0.22f, -radius * 1.0f)
    val pTopR = center + Offset(radius * 0.22f, -radius * 1.0f)
    
    val pCornerL = center + Offset(-radius * 0.52f, -radius * 0.85f)
    val pCornerR = center + Offset(radius * 0.52f, -radius * 0.85f)
    
    val pSideL = center + Offset(-radius * 0.62f, -radius * 0.52f)
    val pSideR = center + Offset(radius * 0.62f, -radius * 0.52f)
    
    val pInnerL = center + Offset(-radius * 0.32f, -radius * 0.32f)
    val pInnerR = center + Offset(radius * 0.32f, -radius * 0.32f)
    
    val pShoulderL = center + Offset(-radius * 0.12f, -radius * 0.12f)
    val pShoulderR = center + Offset(radius * 0.12f, -radius * 0.12f)

    // Fill Right Side Facets
    drawFacet(pCenter, pShoulderR, pInnerR, c1)
    drawFacet(pShoulderR, pMidVein1, pInnerR, c2)
    drawFacet(pMidVein1, pMidVein2, pInnerR, c3)
    drawFacet(pMidVein2, pSideR, pInnerR, c4)
    drawFacet(pMidVein2, pCornerR, pSideR, c5)
    drawFacet(pMidVein2, pTopR, pCornerR, c6)
    drawFacet(pIndentation, pTopR, pMidVein2, c4)
    drawFacet(pSideR, pCornerR, pInnerR, c2)
    drawFacet(pCenter, pInnerR, pSideR, c1)

    // Fill Left Side Facets (mirrored logic or slightly varied colors for depth)
    drawFacet(pCenter, pShoulderL, pInnerL, c2)
    drawFacet(pShoulderL, pMidVein1, pInnerL, c3)
    drawFacet(pMidVein1, pMidVein2, pInnerL, c4)
    drawFacet(pMidVein2, pSideL, pInnerL, c5)
    drawFacet(pMidVein2, pCornerL, pSideL, c6)
    drawFacet(pMidVein2, pTopL, pCornerL, c4)
    drawFacet(pIndentation, pTopL, pMidVein2, c3)
    drawFacet(pSideL, pCornerL, pInnerL, c3)
    drawFacet(pCenter, pInnerL, pSideL, c2)

    // Draw Gold Outlines
    val strokeWidth = 1.dp.toPx()
    val goldPath = Path().apply {
        // Outline
        moveTo(pCenter.x, pCenter.y)
        lineTo(pSideR.x, pSideR.y)
        lineTo(pCornerR.x, pCornerR.y)
        lineTo(pTopR.x, pTopR.y)
        lineTo(pIndentation.x, pIndentation.y)
        lineTo(pTopL.x, pTopL.y)
        lineTo(pCornerL.x, pCornerL.y)
        lineTo(pSideL.x, pSideL.y)
        close()

        // Internal lines
        moveTo(pCenter.x, pCenter.y)
        lineTo(pIndentation.x, pIndentation.y)

        // Right side internal
        moveTo(pShoulderR.x, pShoulderR.y); lineTo(pInnerR.x, pInnerR.y)
        moveTo(pMidVein1.x, pMidVein1.y); lineTo(pInnerR.x, pInnerR.y)
        moveTo(pMidVein2.x, pMidVein2.y); lineTo(pInnerR.x, pInnerR.y)
        moveTo(pMidVein2.x, pMidVein2.y); lineTo(pSideR.x, pSideR.y)
        moveTo(pMidVein2.x, pMidVein2.y); lineTo(pCornerR.x, pCornerR.y)
        moveTo(pMidVein2.x, pMidVein2.y); lineTo(pTopR.x, pTopR.y)
        moveTo(pInnerR.x, pInnerR.y); lineTo(pSideR.x, pSideR.y)
        moveTo(pInnerR.x, pInnerR.y); lineTo(pCornerR.x, pCornerR.y)

        // Left side internal
        moveTo(pShoulderL.x, pShoulderL.y); lineTo(pInnerL.x, pInnerL.y)
        moveTo(pMidVein1.x, pMidVein1.y); lineTo(pInnerL.x, pInnerL.y)
        moveTo(pMidVein2.x, pMidVein2.y); lineTo(pInnerL.x, pInnerL.y)
        moveTo(pMidVein2.x, pMidVein2.y); lineTo(pSideL.x, pSideL.y)
        moveTo(pMidVein2.x, pMidVein2.y); lineTo(pCornerL.x, pCornerL.y)
        moveTo(pMidVein2.x, pMidVein2.y); lineTo(pTopL.x, pTopL.y)
        moveTo(pInnerL.x, pInnerL.y); lineTo(pSideL.x, pSideL.y)
        moveTo(pInnerL.x, pInnerL.y); lineTo(pCornerL.x, pCornerL.y)
    }
    drawPath(goldPath, color = goldColor, style = Stroke(width = strokeWidth))
}

private fun DrawScope.drawFacet(p1: Offset, p2: Offset, p3: Offset, color: Color) {
    val path = Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawCenterStar(center: Offset, radius: Float, brush: Brush) {
    val path = Path()
    val points = 8
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val angle = Math.toRadians(i * 360.0 / (points * 2) - 90.0)
        val x = center.x + r * kotlin.math.cos(angle).toFloat()
        val y = center.y + r * kotlin.math.sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, brush = brush)
    
    // Radiating gold accents
    for (i in 0 until 4) {
        val angle = Math.toRadians(i * 90.0 - 45.0)
        val x = center.x + radius * 2.2f * kotlin.math.cos(angle).toFloat()
        val y = center.y + radius * 2.2f * kotlin.math.sin(angle).toFloat()
        drawLine(
            brush = brush,
            start = center,
            end = Offset(x, y),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GemCloverPreview() {
    ChatAppTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            GemClover()
        }
    }
}
