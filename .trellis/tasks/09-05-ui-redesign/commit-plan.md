# UI 重设计本地提交范围

当前分支：`main`。本轮按用户全局协作规则自主完成任务范围内的可逆工作，以本地提交交付实现和验证记录；外部发布不在本次范围内。

任务工作提交：`feat(ui): 完成 Narra 界面重设计与最终回归`。

包含本轮实现、复核修正，以及当前 ui-redesign 从前几轮延续并完成终审的 UI 改动。前轮文件明确列在下面，按同一任务纳入。提交不包含 APK 二进制、截图、日志、无关 docs/web/images 或其他任务。

构建、Lint、1526 项 JVM 和 97 项设备用例的结果及验证边界见 [verification.md](verification.md)。本次不发布更新，不递增版本。

## 本轮新增或开始修改的候选文件

- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/floating-translation-ui-guidelines.md`
- `.trellis/spec/backend/index.md`
- `app/src/androidTest/java/com/example/myapplication/system/translation/FloatingOverlayControllerTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/component/NarraAlertDialogTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/home/HomeScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/moments/MomentsScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/phone/PhoneCheckScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/RoleplayMessageDeletionUiTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/RoleplayScenarioEditScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/ScreenTranslationSettingsScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/contextlog/ContextLogScreenTest.kt`
- `app/src/main/java/com/example/myapplication/system/translation/FloatingOverlayController.kt`
- `app/src/main/java/com/example/myapplication/system/translation/FloatingOverlayGeometry.kt`
- `app/src/main/java/com/example/myapplication/system/translation/FloatingTranslationViews.kt`
- `app/src/main/java/com/example/myapplication/system/translation/ScreenCapturePermissionActivity.kt`
- `app/src/main/java/com/example/myapplication/system/translation/ScreenTranslationTasks.kt`
- `app/src/main/java/com/example/myapplication/system/translation/ScreenTranslatorService.kt`
- `app/src/main/java/com/example/myapplication/ui/component/ContextGovernanceSheet.kt`
- `app/src/main/java/com/example/myapplication/ui/component/NarraAlertDialog.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/home/HomeScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/home/HomeScreenTokens.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/moments/MomentsScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/phone/PhoneCheckScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/phone/PhoneComponents.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/phone/PhoneDetailContent.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ScreenTranslationSettingsScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/contextlog/ContextLogListContent.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/contextlog/ContextLogScreen.kt`
- `app/src/main/java/com/example/myapplication/viewmodel/MomentsViewModel.kt`
- `app/src/test/java/com/example/myapplication/system/translation/FloatingOverlayGeometryTest.kt`
- `app/src/test/java/com/example/myapplication/system/translation/ScreenTranslationTasksTest.kt`
- `app/src/test/java/com/example/myapplication/ui/theme/AppColorSchemeTest.kt`
- `app/src/test/java/com/example/myapplication/viewmodel/MomentsViewModelTest.kt`

## 从前几轮延续并已终审的 UI 文件

- `.trellis/spec/backend/ui-state-guidelines.md`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/diagnostics/DiagnosticsScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/RoleplayDiaryScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/RoleplaySettingsContentTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/RoleplaySystemBarsTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/RoleplayVideoCallScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/AppUpdateScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/AssistantSettingsRedesignTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/CharacterArtStudioScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/ProviderSettingsUiTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/RoleplayScriptLabScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/SettingsAppearanceScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/SettingsComponentsTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/SettingsScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/VoiceSettingsRedesignTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/VoiceSynthesisSettingsScreenTest.kt`
- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/memory/SimpleMemoryEditorScreenTest.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/compliance/ComplianceScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/diagnostics/DiagnosticsScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/immersive/ImmersivePhoneShell.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayDiaryComponents.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayDiaryDetailScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayDiaryScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayReadingMode.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayScreenContent.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplaySettingsContent.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplaySettingsScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplaySettingsSections.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplaySettingsSidebarPanel.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplaySystemBars.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayVideoCallScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayVoiceMessageSheet.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayWalletScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/mailbox/MailboxScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/AppUpdateScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/AssistantBasicScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/AssistantDetailScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/AssistantExtensionsScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/AssistantListScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/AssistantMemoryScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/AssistantPageComponents.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/AssistantPromptScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/CharacterArtStudioScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ContextTransferScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/PresetManagerScreens.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderConfigTab.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderDetailChrome.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderDetailScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderModelDialogs.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderModelTab.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderSettingsScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderTemplateDialog.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/RoleplayScriptLabScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/SettingsComponents.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/SettingsModelScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/SettingsScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/UserPersonaMasksScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/VoiceRecognitionApiScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/VoiceRecognitionSettingsScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/VoiceSynthesisSettingsScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/memory/MemoryManagementScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/memory/SimpleMemoryEditorScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookContentFullscreenSheet.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookEditScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookEditSections.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookListScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/theme/ChatAppTheme.kt`
- `app/src/main/java/com/example/myapplication/ui/theme/Type.kt`
- `app/src/main/java/com/example/myapplication/ui/theme/UiTokens.kt`

部分前轮文件本轮也有修正，例如主题解析和 Roleplay 说明入口；它们整体属于同一 UI 任务。

## 任务记录

- `.trellis/tasks/09-05-ui-redesign/prd.md`
- `.trellis/tasks/09-05-ui-redesign/design.md`
- `.trellis/tasks/09-05-ui-redesign/implement.md`
- `.trellis/tasks/09-05-ui-redesign/coverage.md`
- `.trellis/tasks/09-05-ui-redesign/verification.md`
- `.trellis/tasks/09-05-ui-redesign/implement.jsonl`
- `.trellis/tasks/09-05-ui-redesign/check.jsonl`
- `.trellis/tasks/09-05-ui-redesign/task.json`
- `.trellis/tasks/09-05-ui-redesign/commit-plan.md`

## 排除范围

- 工作区原有 `.superpowers/`、`docs/` 下未跟踪规划与历史 APK、`images/`、`task/`、`web/`、`改造调研/`。
- 工作区根目录截图、XML、JAR、临时网页和本轮 `%TEMP%/narra-ui-redesign-final-20260906`。
- 当前任务目录里的 PNG 和日志；只提交上面列出的文字记录。
- 导航、数据库结构、版本号、更新元数据和普通 Chat 源码本轮没有改动。

按此清单精确 stage，提交前检查实际 diff，不使用 `git add .`。A4/A9 与系统权限相关验收边界保留，当前任务不归档；后续满足验收条件时用 `task.py archive --no-commit` 并只提交文字记录的移动，避免把本地截图/日志自动纳入。
