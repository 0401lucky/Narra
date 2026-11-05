# P11 工作提交清单

状态：用户已回复“可以没问题。提交归档吧”，确认本清单；执行两笔本地工作提交及后续独立归档/journal，不推送。

## 1. feat(ui): 统一v2.3提供商配置与无障碍行为

本提交共 1313 个文件：8 个生产文件、14 个测试/登记文件、5 个规范/版本/父任务文件，以及 1286 个本任务文档与证据文件。

### 生产文件

- `app/src/main/java/com/example/myapplication/ui/component/NarraAlertDialog.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderConfigTab.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderDetailChrome.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderDetailScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderModelDialogs.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderModelTab.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderSettingsScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderTemplateDialog.kt`

### 测试与登记

- `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/ProviderSettingsFlowTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ProviderColorsTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ProviderConfigAccessibilityTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ProviderDialogsTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ProviderModelsAccessibilityTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ProviderNavigationTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ProviderPersistenceTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ProviderSettingsActionsTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ProviderSharedDialogCompatibilityTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screenshot/ProviderSettingsRenderTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screenshot/ProviderSettingsScreenshotE11Test.kt`
- `app/src/test/java/com/example/myapplication/ui/screenshot/ScreenshotCases.kt`
- `app/src/test/java/com/example/myapplication/ui/theme/UiTokenSourceGuardTest.kt`
- `app/src/test/java/com/example/myapplication/ui/theme/UiTokenSourceInventory.kt`

### 规范、版本与父任务

- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/backend/ui-token-contract.md`
- `.trellis/tasks/10-21-ui-token-accessibility-expansion/implement.md`
- `.trellis/tasks/10-21-ui-token-accessibility-expansion/task.json`
- `版本功能添加/优化.md`

### 本任务文档与证据

仅 `.trellis/tasks/10-21-ui-token-provider-settings/` 内本轮生成/修改的文件，包括规划/验证/独立检查、两次视觉接纳、原始/稳定前后图、差异、语义、JUnit/Lint XML、捕获来源与哈希。原始 `.log` 沿仓库忽略规则留在本地，不强制纳入。精确逐文件清单见 [commit-plan.json](commit-plan.json) 的 `commits[0].files`。

## 2. test(screenshot): 接纳v2.3提供商配置视觉基准

- 32 张已获人工接纳的 `app/src/test/snapshots/ProviderSettingsScreenshotE11Test/*.png`。
- `app/src/test/snapshots/manifest.md` 仅本批新增的 P11 接纳段落。旧 CI 段落差异保留在工作区，不纳入索引。

完整33项见 [commit-plan.json](commit-plan.json) 的 `commits[1].files`。

## 验证

- 159/159 真实截图、1826/1826 完整 JVM 全部通过，均退出0且无失败/错误/跳过。
- Lint 无新增警告；dev/AndroidTest 编译通过。
- 158张已接纳PNG，原126张始终不变；5张稳定帧修正另获确认。
- 构建与更新配置仍2.2.12-dev，功能归属未发布v2.3，不推送、不发包。

## 排除与后续

- 110 条无关工作区状态及manifest既有CI差异不提交；完整原路径见JSON的`excluded_status_entries`。
- 不纳入APK、构建/缓存输出、其他历史任务及已忽略的本地运行日志。
- 两笔工作提交之后，按已授权流程分别归档P11、核对四批总验收并收尾父任务，再记录journal；父任务不执行start。仅同步必要引用。
- 真机读屏/系统返回/IME/系统栏、实际材质、物理性能及新视觉远端CI仍待补。
