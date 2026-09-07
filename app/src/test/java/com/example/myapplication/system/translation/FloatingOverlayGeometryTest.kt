package com.example.myapplication.system.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingOverlayGeometryTest {
    @Test
    fun storedPosition_invalidOrOutOfRange_staysInsideUsableScreen() {
        val geometry = FloatingOverlayGeometry(360, 640, 1f)
        assertEquals(0 to 0, geometry.ballPosition(Float.NaN, Float.POSITIVE_INFINITY))
        assertEquals(0 to 584, geometry.ballPosition(-1f, 2f))
        assertEquals(304 to 584, geometry.clampBall(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun drag_snapToNearestEdge_keepsNormalizedPositionAcrossRotation() {
        val portrait = FloatingOverlayGeometry(360, 640, 1f)
        val snapped = portrait.snapBall(280, 292)
        assertEquals(1f to 0.5f, snapped)
        val landscape = FloatingOverlayGeometry(640, 360, 1f)
        assertEquals(584 to 152, landscape.ballPosition(snapped.first, snapped.second))
        assertEquals(0f to 0f, portrait.snapBall(-100, -100))
    }

    @Test
    fun panel_portraitAndShortLandscape_leaveMarginsAndCapReadingWidth() {
        val portrait = FloatingOverlayGeometry(1080, 1800, 3f)
        assertEquals(984, portrait.panelWidth)
        assertEquals(1440, portrait.panelHeight)
        val landscape = FloatingOverlayGeometry(1800, 720, 3f)
        assertEquals(1200, landscape.panelWidth)
        assertEquals(624, landscape.panelHeight)
        assertTrue(landscape.panelHeight < landscape.height)
    }

    @Test
    fun unusuallySmallWindow_noNegativeWindowSizesOrCoordinates() {
        val geometry = FloatingOverlayGeometry(24, 24, 1f)
        assertEquals(1, geometry.panelWidth)
        assertEquals(1, geometry.panelHeight)
        assertEquals(0 to 0, geometry.ballPosition(1f, 1f))
        assertEquals(0f to 0f, geometry.snapBall(100, 100))
    }
}
