package com.example.myapplication.ui.theme

import com.example.myapplication.ui.theme.UiTokenSourceGuard.Exception
import com.example.myapplication.ui.theme.UiTokenSourceGuard.Rule
import com.example.myapplication.ui.theme.UiTokenSourceGuard.Scope

/** 路径均相对生产 ui/；新增文件、移位声明与未消费例外必须显式审阅。 */
internal object UiTokenSourceInventory {
    val scopes = listOf(
        Scope("theme/UiTokens.kt", setOf("NarraUi", "NarraShapes", "NarraMinimumTouchTarget")),
        Scope("theme/ChatAppTheme.kt", setOf("ChatAppTheme", "resolveAppColorScheme")),
        Scope("theme/RoleplaySkinPalette.kt", setOf("roleplayBrandSkinPalette", "roleplayTextContrast", "readableRoleplayText", "readableRoleplayTextOnSurfaces")),
        Scope("component/NarraButton.kt", setOf("NarraIconButton")),
        Scope("component/roleplay/RoleplayUiTokens.kt", setOf("RoleplayGlassTokens", "RoleplayDialogueReadingMinimumAlpha")),
        Scope("component/roleplay/RoleplayNoBackgroundSkin.kt", setOf("DefaultRoleplayDialogueBubbleStyle", "resolveRoleplayNoBackgroundSkinSpec")),
        Scope("component/roleplay/RoleplayDialoguePanelParts.kt", setOf("ImmersiveRoleplayColors", "rememberImmersiveRoleplayColors")),
        Scope("component/roleplay/RoleplayDialogueBubbles.kt", setOf("UserDialogueBubbleContent", "CharacterDialogueBubbleContent")),
        Scope("component/roleplay/RoleplayMessageBubbles.kt", setOf("RoleplayMessageItem", "RoleplayMessageItemContent")),
        Scope("component/roleplay/RoleplayInputBarComponents.kt", setOf("RoleplayInputBar")),
        Scope("component/roleplay/RoleplayOnlinePhoneBubbles.kt", setOf("OnlinePhoneNarrationBubble", "OnlinePhoneThoughtBubble")),
        Scope("screen/immersive/ImmersivePhoneShell.kt", setOf("ImmersivePhoneShell", "ImmersiveBottomBar", "ChatSummaryRow")),
        Scope("screen/settings/AssistantListScreen.kt", setOf("AssistantListScreen", "AssistantCardMinimal")),
        Scope("screen/roleplay/RoleplayOnlinePhoneContent.kt", setOf("RoleplayOnlinePhoneContent", "rememberOnlinePhoneColors")),
        Scope("screen/roleplay/RoleplayScreen.kt", setOf("RoleplayScreen")),
        Scope("screen/roleplay/RoleplayScreenCallbacks.kt", setOf("RoleplayScreenCallbacks")),
        Scope("screen/roleplay/RoleplayReadingMode.kt", setOf("RoleplayReadingMode", "ReadingModeColors", "resolveReadingModeColors")),
        Scope("screen/roleplay/RoleplaySettingsScreen.kt", setOf("RoleplaySettingsScreen")),
        Scope("screen/roleplay/RoleplaySettingsContent.kt",
            scannedDeclarations = setOf("TAG_ROLEPLAY_SETTINGS_LIST", "RoleplaySettingsContent", "RoleplaySettingsModelSheet",
                "RoleplayRestartConfirmDialog", "RoleplayResetConfirmDialog", "RoleplaySettingSwitchRow", "ImmersiveSettingsCard"),
            legacyDeclarations = setOf("RoleplaySettingsHero")),
        Scope("screen/roleplay/RoleplaySettingsSidebarPanel.kt", setOf("RoleplaySettingsSidebarContent", "RoleplaySettingsMainPanel")),
        Scope("screen/roleplay/RoleplaySettingsSections.kt", setOf("RoleplayNoBackgroundThemeSection", "RoleplaySkinPreviewCard", "RoleplaySettingsReadabilitySection")),
        Scope("component/roleplay/ImmersiveTabRow.kt", setOf("ImmersiveTabRow")),
        Scope("component/roleplay/RoleplayDialoguePanel.kt", setOf("RoleplayDialoguePanel")),
        Scope("component/roleplay/RoleplayPortraitLayer.kt", setOf("RoleplayPortraitLayer", "PortraitCard")),
        Scope("screen/roleplay/RoleplayScreenContent.kt",
            scannedDeclarations = setOf("RoleplaySceneContent", "PendingMemoryProposalCard", "RoleplaySceneTopBar",
                "RoleplayChromeHandle", "RoleplayPresenceStrip", "resolveMoodAccentColor"),
            legacyDeclarations = setOf("RoleplaySpecialPlayOverlays", "RoleplayAssistantMismatchDialog")),
        Scope(
            "component/roleplay/ImmersiveGlass.kt",
            scannedDeclarations = setOf(
                "paletteCache", "ImmersivePaletteCacheKey", "immersivePaletteCacheKey", "rememberImmersiveBackdropState",
                "readPaletteCache", "writePaletteCache", "ImmersiveReadingGlassSurface", "roleplayDialogueReadingSurfaces",
            ),
            legacyDeclarations = setOf(
                "ImmersiveBackdropMaxDecodeSidePx", "ImmersiveGlassPalette", "ImmersiveBackdropState", "ImmersiveReadingGlassSpec", "ImmersiveReadingGlassVariant",
                "ImmersiveReadingScrimVariant", "ImmersiveBackdrop", "IntSize.coerceForImmersiveBackdropRequest",
                "ImmersiveGlassSurface", "resolveImmersiveReadingGlassSpec", "resolveImmersiveReadingScrimAlpha",
                "calculateImmersiveBackdropAmbientLuminance", "adjustImmersiveReadingOverlayColor",
                "adjustImmersiveReadingContentDarkenAlpha", "adjustImmersiveReadingBorderAlpha", "defaultReadingOverlayColor",
                "defaultReadingBorderColor", "readingContrastFactor", "lerp", "ImmersiveGlassChip", "AlignedBlurredBackdropSlice",
                "deriveImmersiveGlassPalette", "fallbackImmersiveGlassPalette", "ImmersiveContrastProfile", "NormalContrastProfile",
                "HighContrastProfile", "colorFromHsl", "sampleAverageColor", "calculateTopRegionLuminance",
                "calculateBottomRegionLuminance", "Modifier.glassTextContrast", "LocalImmersiveHazeState",
            ),
        ),
        Scope(
            "screen/settings/SettingsComponents.kt",
            scannedDeclarations = setOf("SettingsTopBar"),
            legacyDeclarations = setOf(
                "SettingsScreenPadding", "SettingsGroupSpacing", "SettingsGroupRadius", "SettingsAvatarRadius", "SettingsPalette",
                "rememberSettingsPalette", "SettingsPageIntro", "SettingsHelpButton", "SettingsDescriptionDialog", "SettingsSectionHeader",
                "SettingsGroup", "SettingsGroupDivider", "SettingsListRow", "SettingsPlaceholderRow", "SettingsStatusPill", "SettingsHintCard",
                "SettingsNoticeCard", "SettingsLetterAvatar", "SettingsModelAvatar", "SettingsModelOptionRow", "AnimatedSettingButton",
                "rememberSettingsSnackbarHostState", "rememberSettingsOutlineColors",
            ),
        ),
    )

    val exceptions: List<Exception> = listOf(
        Exception("screen/roleplay/RoleplaySettingsScreen.kt", "RoleplaySettingsScreen", Rule.COLOR, "Color.Black", "侧栏外的场景遮罩，保留原背景对比意图"),
        Exception("screen/roleplay/RoleplaySettingsSidebarPanel.kt", "RoleplaySettingsMainPanel", Rule.COLOR, "Color.Transparent", "主列表继承实际面板背景"),
        Exception("screen/roleplay/RoleplaySettingsSidebarPanel.kt", "ScenarioPresetPickRow", Rule.COLOR, "Color.Transparent", "未选择的预设行继承Sheet背景"),
        Exception("screen/roleplay/RoleplaySettingsSidebarPanel.kt", "SummaryDivider", Rule.SPACING,
            "padding(start = 56.dp, end = NarraUi.space16)", "分隔线对齐既有图标后的正文，不作为默认布局间距"),
        Exception("screen/roleplay/RoleplaySettingsSections.kt", "SectionDivider", Rule.SPACING,
            "padding(start = 56.dp, end = NarraUi.space16)", "开关分隔线对齐图标后的正文"),
        Exception("screen/roleplay/RoleplaySettingsSections.kt", "roleplaySettingsOutlinedTextFieldColors", Rule.COLOR,
            "Color.Transparent", "数字输入继承实际设置卡片背景", 2),
        Exception("component/roleplay/ImmersiveTabRow.kt", "ImmersiveTabRow", Rule.COLOR, "Color.Transparent", "未选中选项的透明边框"),
        Exception("theme/UiTokens.kt", "NarraShapes", Rule.SHAPE, "RoundedCornerShape(percent = 50)", "共享全圆令牌的唯一相对尺寸定义"),
        Exception("screen/immersive/ImmersivePhoneShell.kt", "PhoneDestinationRow", Rule.SPACING,
            "padding(start = NarraUi.space24 + 39.dp, end = NarraUi.space24)", "分隔线对齐图标后的文字：23dp 图标 + 16dp 行间距"),
        Exception("screen/settings/SettingsComponents.kt", "SettingsTopBar", Rule.SPACING, "spacedBy(2.dp)",
            "非试点设置页的标题 / 副标题历史间距；P02 不传副标题，不扩大全设置页布局变更"),
        Exception("component/roleplay/ImmersiveGlass.kt", "ImmersiveReadingGlassSurface", Rule.SHAPE, "RoundedCornerShape(28.dp)",
            "共享玻璃 Surface 的非试点兼容默认；试点消息显式传入统一形状"),
        Exception("theme/ChatAppTheme.kt", "resolveAppColorScheme", Rule.COLOR, "Color.White", "应用 ColorScheme 基色所有者", 4),
        Exception("theme/RoleplaySkinPalette.kt", "roleplayBrandSkinPalette", Rule.COLOR, "Color.White", "十二套品牌调色中的固定白色", 30),
        Exception("theme/RoleplaySkinPalette.kt", "readableRoleplayTextOnSurfaces", Rule.COLOR, "Color.Black", "对比度不足时的黑 / 白候选端点"),
        Exception("theme/RoleplaySkinPalette.kt", "readableRoleplayTextOnSurfaces", Rule.COLOR, "Color.White", "对比度不足时的黑 / 白候选端点"),
        Exception("component/roleplay/RoleplayDialoguePanelParts.kt", "EmptyDialogueState", Rule.COLOR, "Color.Transparent", "线下空态光晕的透明渐变端点"),
        Exception("component/roleplay/RoleplayInputBarComponents.kt", "RoleplayInputBar", Rule.COLOR, "Color.Transparent", "输入区禁用态的透明背景"),
        Exception("component/roleplay/RoleplayInputBarComponents.kt", "RoleplayQuickActionPanel", Rule.COLOR, "Color.White", "先以白色作为图标前景，再按真实承载面校正对比度"),
        Exception("screen/immersive/ImmersivePhoneShell.kt", "ImmersiveBottomBar", Rule.COLOR, "Color.Transparent", "未选择导航项的透明背景"),
        Exception("screen/immersive/ImmersivePhoneShell.kt", "ShakeHeroButton", Rule.COLOR, "Color.Transparent", "装饰渐变的透明端点"),
        Exception("screen/roleplay/RoleplayOnlinePhoneContent.kt", "OnlinePhoneHeaderButton", Rule.COLOR, "Color.Transparent", "无填充头部按钮的透明背景"),
        Exception("component/roleplay/ImmersiveGlass.kt", "ImmersiveReadingGlassSurface", Rule.COLOR, "Color.Transparent", "玻璃 sheen 的透明渐变端点"),
        Exception("component/roleplay/ImmersiveGlass.kt", "roleplayDialogueReadingSurfaces", Rule.COLOR, "Color.Black", "复算真实 scrim、正文加深与局部全黑背景合成面", 3),
        Exception("component/roleplay/ImmersiveGlass.kt", "roleplayDialogueReadingSurfaces", Rule.COLOR, "Color.White", "局部全白背景的最差对比候选端点"),
        Exception("component/roleplay/ImmersiveGlass.kt", "roleplayDialogueReadingSurfaces", Rule.COLOR, "Color.Transparent", "复算真实 sheen 的透明端点"),
        Exception("screen/settings/SettingsComponents.kt", "SettingsTopBar", Rule.COLOR, "Color.Transparent", "共享顶栏保留父级阅读表面的透明背景"),
        Exception("screen/roleplay/RoleplayScreenContent.kt", "RoleplaySceneContent", Rule.COLOR, "Color.Transparent", "实际场景scrim的透明渐变端点", 3),
        Exception("screen/roleplay/RoleplayScreenContent.kt", "resolveMoodAccentColor", Rule.COLOR, "Color(0xFFD7E7FF)", "平稳氛围装饰与既有场景遮罩色，不用作阅读文字"),
        Exception("screen/roleplay/RoleplayScreenContent.kt", "resolveMoodAccentColor", Rule.COLOR, "Color(0xFFF8C97E)", "温暖氛围装饰与既有场景遮罩色"),
        Exception("screen/roleplay/RoleplayScreenContent.kt", "resolveMoodAccentColor", Rule.COLOR, "Color(0xFF86B2FF)", "紧张氛围装饰与既有场景遮罩色"),
        Exception("screen/roleplay/RoleplayScreenContent.kt", "resolveMoodAccentColor", Rule.COLOR, "Color(0xFFFF8A8A)", "激烈氛围装饰与既有场景遮罩色"),
        Exception("screen/roleplay/RoleplayScreenContent.kt", "resolveMoodAccentColor", Rule.COLOR, "Color(0xFFC7BEEB)", "低落氛围装饰与既有场景遮罩色"),
        Exception("component/roleplay/RoleplayPortraitLayer.kt", "RoleplayPortraitLayer", Rule.COLOR, "Color.Unspecified", "未传立绘强调色时沿用Material容器角色", 4),
        Exception("component/roleplay/RoleplayPortraitLayer.kt", "PortraitCard", Rule.COLOR, "Color.Transparent", "立绘图像承载层与渐变透明端点", 2),
        Exception("screen/roleplay/RoleplayReadingMode.kt", "RoleplayReadingMode", Rule.COLOR, "Color.Black", "阅读页实际背景scrim三档透明度", 3),
        Exception("screen/roleplay/RoleplayReadingMode.kt", "DialogueReadingBlock", Rule.COLOR, "Color.Transparent", "情绪标签继承已验证的对白阅读面"),
        Exception("screen/roleplay/RoleplayReadingMode.kt", "LongformReadingBlock", Rule.COLOR, "Color.Transparent", "长文卡沿用独立阅读玻璃材质"),
        Exception("screen/roleplay/RoleplayReadingMode.kt", "resolveReadingModeColors", Rule.COLOR, "Color.Transparent", "复算实际玻璃sheen透明端点"),
        Exception("screen/roleplay/RoleplayReadingMode.kt", "resolveReadingModeColors", Rule.COLOR, "Color.Black", "复算实际正文加深与背景最暗端点", 2),
        Exception("screen/roleplay/RoleplayReadingMode.kt", "resolveReadingModeColors", Rule.COLOR, "Color.White", "复算背景最亮端点"),
    ) + colors("theme/ChatAppTheme.kt", "resolveAppColorScheme", """
        FF326B58 FF79552D FF326692 FF141617 FFF5F6F7 FFE6E8E9 FF212628 FF191C1D
        FFE6E8E9 FF212628 FF303638 FFE9EDEF FFB9C1C4 FF566267 FF101213 FF1D2123
        FFF9FAFB FF222729 FFF0F3F4 FF2A3032 FFE9EDEF FF343B3D FFE2E7E9 FF899498
        FF77858B FF3B4448 FFDDE3E6 FFFFB4AB FFB3261E FF690005
    """) + colors("theme/ChatAppTheme.kt", "VanillaLightColors", """
        FFC7A87D FFFFFFFF FFF3E8D8 FF4A3B2C FFDCC8B3 FF3D3124 FFF6EFE6 FF524536
        FFB89B72 FFFFFFFF FFE8DBC9 FF453625 FFE57373 FFFFEBEE FFFFFFFF FFB71C1C
        FFFAF6F0 FF26221E FFFAF6F0 FF26221E FFE8E1D7 FF524B43 FF8A8277 FFF6F3EF
        FF3B3733 FFE1C5A1
    """) + colors("theme/ChatAppTheme.kt", "VanillaDarkColors", """
        FFE1C5A1 FF3D3124 FF4A3B2C FFF3E8D8 FFE6D5C3 FF33271A FF453625 FFF6EFE6
        FFD1B792 FF362817 FF4D3B26 FFE8DBC9 FFEF9A9A FFB71C1C FF370B0B FFFFEBEE
        FF1E1B18 FFEBE6E0 FF1E1B18 FFEBE6E0 FF524B43 FFD4CCBF FF9E958A FF1E1B18
        FFEBE6E0 FFC7A87D
    """) + colors("theme/ChatAppTheme.kt", "OceanLightColors", """
        FF6B9ED1 FFFFFFFF FFD5E5F6 FF163254 FF8AB5E0 FFFFFFFF FFE2EFFB FF1C3A5A
        FF5A8DBE FFFFFFFF FFCBE0F4 FF122C4A FFE57373 FFFFEBEE FFFFFFFF FFB71C1C
        FFF2F7FC FF161B21 FFF2F7FC FF161B21 FFDDE6EF FF3D4958 FF758394 FFEFF3F8
        FF28303A FFA1C6ED
    """) + colors("theme/ChatAppTheme.kt", "OceanDarkColors", """
        FFA1C6ED FF163254 FF2B4D73 FFD5E5F6 FFB5D3F2 FF162740 FF28486A FFE2EFFB
        FF93BAE5 FF122C4A FF234466 FFCBE0F4 FFEF9A9A FFB71C1C FF370B0B FFFFEBEE
        FF0F1318 FFDFE6EE FF0F1318 FFDFE6EE FF3D4958 FFC2CDDA FF8B9BAE FF0F1318
        FFDFE6EE FF6B9ED1
    """) + colors("theme/RoleplaySkinPalette.kt", "roleplayBrandSkinPalette", """
        FFEDEDED FFDADDE3 FF07C160 FF95EC69 FF111111 FF111111 FF6D7784
        FFEAF3FF FFD5E4F7 FF0099FF FF0099FF FF1D2530 FF69768A
        FFE6F3FA FFF9FDFF FFCFE1EA FF2AABEE FFD8F2FF FF102635 FF102635 FF607685
        FFBACEDE FFF8FBFE FF9FB4C8 FFFEE500 FFFEE500 FF191600 FF18212B FF596D80
        FFE9EEF5 FFFFFCF6 FFBCC7D3 FF5CA7F8 FF5CA7F8 FFFFFCF6 FF263241 FF687585
        FFF5E6CF FFFFF8EE FFFFFBF4 FFE5C9A7 FFD85A50 FFD85A50 FFFFF8EE FF3A2D25 FF8A6F5C
        FFECEAF3 FFFFFBF0 FF4D5D79 FF4B7BD7 FF4B7BD7 FFFFFBF0 FF172033 FF5C6880
        FFFFEDF4 FFF2C8D7 FFE58BAD FFF5DDE6 FF3A2130 FF3A2130 FF9A667A
        FF0E0B14 FF171120 FF21172F FF5D4C70 FFB7A0D6 FF21172F FF171120 FFF4EDFF FFF4EDFF FFC1B3D5
        FFF5F8FF FFCFE0FF FF4E9DFF FFCCE4FF FF122641 FF122641 FF6B7C98
        FFF2F2F7 FFD7D7DF FF0A84FF FF0A84FF FFE5E5EA FF111111 FF6E6E73
        FFEAF0F7 FF7CA7D9 FF1E314A FF1E314A FF62748A
    """, "用户明确选择的 12 套品牌皮肤调色；Narra 不在固定品牌表内，阅读对比另由行为测试验证")

    /** 每个十六进制值生成准确 Color 调用，重复值仅允许列出的次数，不按文件总数量放行。 */
    private fun colors(
        path: String,
        declaration: String,
        values: String,
        reason: String = "应用主题所有者定义 ColorScheme 基色，业务调用必须读取语义角色",
    ): List<Exception> = values.trim().split(Regex("\\s+")).groupingBy { it }.eachCount().map { (value, count) ->
        Exception(path, declaration, Rule.COLOR, "Color(0x$value)", reason, count)
    }

}
