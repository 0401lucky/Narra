package com.example.myapplication.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTokenSourceGuardTest {
    @Test
    fun migratedSources_haveNoUnapprovedDefaultsOrStaleExceptions() {
        val root = listOf(
            File("src/main/java/com/example/myapplication/ui"),
            File("app/src/main/java/com/example/myapplication/ui"),
        ).firstOrNull { it.isDirectory } ?: error("无法定位 UI 源码目录")
        val sources = UiTokenSourceInventory.scopes.mapNotNull { scope ->
            File(root, scope.path).takeIf { it.isFile }?.let { scope.path to it.readText() }
        }.toMap()
        val issues = UiTokenSourceGuard.audit(sources, UiTokenSourceInventory.scopes, UiTokenSourceInventory.exceptions)
        assertTrue(issues.joinToString("\n"), issues.isEmpty())

        // 同一试点拆出新文件后必须先登记，不能通过移动代码离开扫描范围。
        val paths = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSet()
        val unregistered = UiTokenSourceGuard.unregisteredPilotFiles(paths, UiTokenSourceInventory.scopes)
        assertTrue("新增试点文件未登记：$unregistered", unregistered.isEmpty())
    }

    @Test
    fun scan_literalDefaultShapesColorsAndLayoutExpressions_areReported() {
        val findings = scan(
            """
            fun Screen() {
                val shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = radius)
                val pill = RoundedCornerShape(percent = 50)
                val color = Color(0xff_123456)
                Modifier.padding(horizontal = 8.dp, vertical = 0.dp)
                val contentPadding = PaddingValues(start = insets.start + 16.dp)
                Arrangement.spacedBy(4.dp)
                Spacer(modifier = Modifier.height(12.dp))
            }
            """.trimIndent(),
        )
        assertEquals(2, findings.count { it.key.rule == UiTokenSourceGuard.Rule.SHAPE })
        assertEquals(1, findings.count { it.key.rule == UiTokenSourceGuard.Rule.COLOR })
        assertEquals(4, findings.count { it.key.rule == UiTokenSourceGuard.Rule.SPACING })
        assertTrue(findings.all { it.key.declaration == "Screen" })
        assertEquals(2, findings.first().line)
    }

    @Test
    fun scan_multilineAndQualifiedCallsAndArithmetic_areReported() {
        val findings = scan(
            """
            private val DefaultShape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
            fun Screen() {
                Modifier.absolutePadding(
                    top = paddingValues.calculateTopPadding() + 0.5f.dp,
                )
                Arrangement.spacedBy(-2.dp)
                Spacer(Modifier.width(24 . dp))
            }
            """.trimIndent(),
        )
        assertEquals(4, findings.size)
        assertEquals("DefaultShape", findings.first().key.declaration)
        assertTrue(findings.drop(1).all { it.key.declaration == "Screen" })
    }

    @Test
    fun scan_fixedRgbDecimalAndNamedColors_failWhileUserArgbAndChannelsAreAllowed() {
        val findings = scan(
            """
            fun Screen() {
                Color(16711680)
                Color(color = 0xFF123456)
                Color(red = 1f, green = 0f, blue = 0f)
                Color(1f, 0.0f, .5f, opacity)
                Color.Red
                Color.White
                Color(userArgb)
                Color(color = settings.userColor)
                Color(red = userRed, green = userGreen, blue = userBlue, alpha = 1f)
                Color(userRed, userGreen, userBlue, 1f)
            }
            """.trimIndent(),
        )
        assertEquals(6, findings.size)
        assertTrue(findings.all { it.key.rule == UiTokenSourceGuard.Rule.COLOR })
    }

    @Test
    fun sourceRegistration_componentAndThemeHelpersRequireInventory() {
        val scopes = listOf(
            UiTokenSourceGuard.Scope("theme/RoleplaySkinPalette.kt"),
            UiTokenSourceGuard.Scope("component/roleplay/RoleplayDialogueBubbles.kt"),
        )
        val helpers = setOf("theme/RoleplaySkinPaletteHelpers.kt", "component/roleplay/RoleplayDialogueBubblesColors.kt")
        val paths = helpers + scopes.map { it.path } + "screen/Unrelated.kt"
        assertEquals(helpers, UiTokenSourceGuard.unregisteredPilotFiles(paths, scopes))
    }

    @Test
    fun scan_tokensUserGeometryIconBorderWidthAndSystemInsets_areAllowed() {
        assertTrue(
            scan(
                """
                fun Screen() {
                    val shape = RoundedCornerShape(normalized.bubbleRadiusDp.dp)
                    Modifier.padding(horizontal = skin.paddingHorizontal, vertical = NarraUi.space8)
                    Modifier.padding(WindowInsets.systemBars.asPaddingValues())
                    Modifier.padding(0.dp).absolutePadding(top = 0.0f.dp)
                    Arrangement.spacedBy(NarraUi.space12)
                    val padding = PaddingValues(0.dp)
                    Spacer(Modifier.weight(1f))
                    Icon(modifier = Modifier.size(18.dp))
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    Modifier.widthIn(max = 320.dp).offset(y = 2.dp)
                    val avatarSize = 48.dp
                    val userRadius = normalized.bubbleRadiusDp.dp
                }
                """.trimIndent(),
            ).isEmpty(),
        )
    }

    @Test
    fun scan_commentsEscapedStringsRawStringsAndCharacters_areIgnored() {
        val source = listOf(
            "// Color(0xFF123456) RoundedCornerShape(12.dp)",
            "/* padding(8.dp) /* Spacer(Modifier.height(12.dp)) */ Color(0xFF123456) */",
            "val ordinary = \"escaped \\\" Color(0xFF123456) padding(8.dp)\"",
            "val raw = " + "\"\"\"" + "RoundedCornerShape(12.dp)\nspacedBy(8.dp)" + "\"\"\"",
            "val slash = '/'",
            "fun RealScreen() { Modifier.padding(16.dp) }",
        ).joinToString("\n")
        val findings = scan(source)
        assertEquals(1, findings.size)
        assertEquals("RealScreen", findings.single().key.declaration)
        assertEquals(7, findings.single().line)
    }

    @Test
    fun audit_brandPalette_requiresExactPathDeclarationValueAndMultiplicity() {
        val source = "private fun brandPalette() = Color(0xFF07C160)"
        val exception = exception("brandPalette", UiTokenSourceGuard.Rule.COLOR, "Color(0xFF07C160)")
        assertTrue(audit(source, listOf(exception)).isEmpty())
        assertTrue(audit(source.replace("07C160", "07C161"), listOf(exception)).any { "新增硬编码" in it })
        assertTrue(audit(source.replace("brandPalette", "newPalette"), listOf(exception)).any { "例外未精确消费" in it })
        assertTrue(audit(source, listOf(exception.copy(path = "Other.kt"))).any { "新增硬编码" in it })
        assertTrue(audit("private fun brandPalette() = listOf(Color(0xFF07C160), Color(0xFF07C160))", listOf(exception))
            .any { "实际 2" in it })
    }

    @Test
    fun audit_removedOrDuplicateExceptions_failInsteadOfSilentlyExpandingWhitelist() {
        val exception = exception("brandPalette", UiTokenSourceGuard.Rule.COLOR, "Color(0xFF07C160)")
        assertTrue(audit("private fun brandPalette() = scheme.primary", listOf(exception)).any { "实际 0" in it })
        assertTrue(audit("private fun brandPalette() = Color(0xFF07C160)", listOf(exception, exception)).any { "重复例外" in it })
    }

    @Test
    fun audit_relocatedDefaultsAndNewHelpers_cannotEscapeRegisteredScope() {
        val source = """
            private val MovedDefault = RoundedCornerShape(18.dp)
            fun Screen() = Unit
        """.trimIndent()
        assertTrue(audit(source).any { "MovedDefault" in it && "新增硬编码" in it })
        val scoped = UiTokenSourceGuard.Scope(
            path = Path,
            scannedDeclarations = setOf("Screen"),
            legacyDeclarations = setOf("Legacy"),
        )
        val issues = UiTokenSourceGuard.audit(
            mapOf(Path to "$source\nfun Legacy() = Unit"),
            listOf(scoped),
        )
        assertTrue(issues.any { "未登记声明" in it && "MovedDefault" in it })
    }

    @Test
    fun audit_missingDuplicateDeclarationsAndMissingOrNewFiles_fail() {
        val scope = UiTokenSourceGuard.Scope(Path, requiredDeclarations = setOf("Screen"))
        assertTrue(UiTokenSourceGuard.audit(emptyMap(), listOf(scope)).any { "缺少扫描文件" in it })
        assertTrue(UiTokenSourceGuard.audit(mapOf("New.kt" to "fun Screen() = Unit"), listOf(scope))
            .any { "未登记扫描文件" in it })
        assertTrue(UiTokenSourceGuard.audit(mapOf(Path to "fun Other() = Unit"), listOf(scope))
            .any { "声明必须存在且唯一" in it })
        assertTrue(UiTokenSourceGuard.audit(mapOf(Path to "fun Screen() = Unit\nfun Screen(x: Int) = Unit"), listOf(scope))
            .any { "声明必须存在且唯一" in it })
    }

    @Test
    fun audit_preciseGeometryException_doesNotApproveOtherLayoutInSameDeclaration() {
        val exception = exception("Screen", UiTokenSourceGuard.Rule.SPACING, "Spacer(Modifier.width(1.dp))")
        val source = "fun Screen() { Spacer(Modifier.width(1.dp)); Modifier.padding(12.dp) }"
        val issues = audit(source, listOf(exception))
        assertEquals(1, issues.size)
        assertTrue(issues.single().contains("padding(12.dp)"))
    }

    private fun scan(source: String) = UiTokenSourceGuard.scan(Path, source)

    private fun audit(source: String, exceptions: List<UiTokenSourceGuard.Exception> = emptyList()) =
        UiTokenSourceGuard.audit(mapOf(Path to source), listOf(UiTokenSourceGuard.Scope(Path)), exceptions)

    private fun exception(declaration: String, rule: UiTokenSourceGuard.Rule, expression: String) =
        UiTokenSourceGuard.Exception(Path, declaration, rule, expression, "受测的精确品牌色 / 几何例外")

    private companion object {
        const val Path = "Pilot.kt"
    }
}
