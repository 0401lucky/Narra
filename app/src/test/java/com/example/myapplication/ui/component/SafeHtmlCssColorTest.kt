package com.example.myapplication.ui.component

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeHtmlCssColorTest {
    private fun colorOf(html: String): Color? =
        parseSafeHtmlTextBlocks(html)?.firstOrNull()?.color

    @Test
    fun eightDigitHexParsesRrggbbaaAsArgb() {
        // CSS #RRGGBBAA：R=11 G=22 B=33 A=44 → Android ARGB 0x44112233
        assertEquals(Color(0x44112233), colorOf("""<p style="color:#11223344">文字</p>"""))
    }

    @Test
    fun eightDigitHexKeepsHighAlpha() {
        // alpha=ff（不透明），其余 RRGGBB 原样前移
        assertEquals(Color(0xFFAABBCC), colorOf("""<p style="color:#aabbccff">文字</p>"""))
    }

    @Test
    fun sixDigitHexStaysOpaque() {
        assertEquals(Color(0xFFAABBCC), colorOf("""<p style="color:#aabbcc">文字</p>"""))
    }

    @Test
    fun threeDigitHexExpandsToOpaque() {
        assertEquals(Color(0xFFAABBCC), colorOf("""<p style="color:#abc">文字</p>"""))
    }
}
